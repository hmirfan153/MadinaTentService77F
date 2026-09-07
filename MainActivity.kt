package com.madinatent.service77f

import android.app.*
import android.content.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import org.json.JSONArray
import org.json.JSONObject

private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

class MainActivity : ComponentActivity() {
    private lateinit var db: AppDb
    private var exportJson: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = AppDb.create(this)
        lifecycleScope.launch {
            if (db.stockDao().allOnce().isEmpty()) {
                db.stockDao().insertAll(listOf(
                    Stock(name="ٹینٹ", totalQty=10, rent=200),
                    Stock(name="کرسی", totalQty=500, rent=100),
                    Stock(name="میز", totalQty=200, rent=100),
                    Stock(name="پلیٹ", totalQty=1000, rent=5),
                    Stock(name="جگ", totalQty=500, rent=10),
                    Stock(name="گلاس", totalQty=400, rent=0),
                    Stock(name="دیگیں", totalQty=15, rent=200),
                    Stock(name="پارات", totalQty=15, rent=0),
                    Stock(name="دریاں", totalQty=200, rent=25),
                    Stock(name="رسے", totalQty=20, rent=0),
                    Stock(name="بانس", totalQty=10, rent=0)
                ))
            }
        }
        setContent { AppUI() }
    }

    private fun share(text: String) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
        }, "شیئر کریں"))
    }

    private fun receiptPdf(booking: Booking, customer: Customer, items: List<BookingItem>, stocks: List<Stock>) {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val c = page.canvas
        val p = android.graphics.Paint().apply { textSize = 18f }
        c.drawText("Madina Tent Service 77/F", 180f, 45f, p)
        p.textSize = 14f
        c.drawText("Customer: ${customer.name}", 40f, 85f, p)
        c.drawText("Phone: ${customer.phone}", 40f, 108f, p)
        c.drawText("Date: ${booking.date}", 40f, 131f, p)
        var y = 165f
        items.forEach { it ->
            val s = stocks.firstOrNull { st -> st.id == it.stockId }
            c.drawText("${s?.name ?: ""} x ${it.qty} = ${it.qty * it.rent}", 40f, y, p)
            y += 24
        }
        c.drawText("Total: ${booking.total}", 40f, y + 10, p)
        c.drawText("Advance: ${booking.advance}", 40f, y + 34, p)
        c.drawText("Balance: ${booking.total - booking.advance}", 40f, y + 58, p)
        doc.finishPage(page)
        val dir = externalCacheDir ?: cacheDir
        val f = java.io.File(dir, "receipt_${booking.id}.pdf")
        java.io.FileOutputStream(f).use { doc.writeTo(it) }
        doc.close()
        val uri = androidx.core.content.FileProvider.getUriForFile(this, "${packageName}.provider", f)
        shareUri(uri)
    }

    private fun shareUri(uri: Uri) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "PDF رسید شیئر کریں"))
    }

    private val createBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) contentResolver.openOutputStream(uri)?.use { it.write(exportJson.toByteArray()) }
    }
    private val openBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lifecycleScope.launch { restore(uri) }
    }

    private suspend fun makeBackup(): String {
        val o = JSONObject()
        o.put("version", 6)
        o.put("customers", JSONArray(db.customerDao().all().toString()))
        o.put("stocks", JSONArray(db.stockDao().allOnce().map { JSONObject().apply {
            put("id", it.id); put("name", it.name); put("totalQty", it.totalQty); put("rent", it.rent)
        }}))
        o.put("bookings", JSONArray(db.bookingDao().all().toString()))
        o.put("bookingItems", JSONArray(db.bookingItemDao().allOnce().map { JSONObject().apply {
            put("id",it.id);put("bookingId",it.bookingId);put("stockId",it.stockId);put("qty",it.qty);put("rent",it.rent)
        }}))
        o.put("returns", JSONArray(db.returnDao().allOnce().map { JSONObject().apply {
            put("id",it.id);put("bookingId",it.bookingId);put("stockId",it.stockId);put("returnedQty",it.returnedQty);put("missingQty",it.missingQty);put("damagedQty",it.damagedQty);put("date",it.date)
        }}))
        o.put("payments", JSONArray(db.paymentDao().allOnce().map { JSONObject().apply {
            put("id",it.id);put("bookingId",it.bookingId);put("amount",it.amount);put("date",it.date);put("note",it.note)
        }}))
        return o.toString(2)
    }

    private suspend fun restore(uri: Uri) {
        val text = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return
        val o = JSONObject(text)
        if (o.optInt("version", 0) != 6) return
        // Restore is intentionally additive to protect existing data.
        val sArr = o.optJSONArray("stocks") ?: JSONArray()
        val current = db.stockDao().allOnce().associateBy { it.name }
        val missing = mutableListOf<Stock>()
        for (i in 0 until sArr.length()) {
            val x=sArr.getJSONObject(i); val name=x.getString("name")
            if (!current.containsKey(name)) missing += Stock(name=name,totalQty=x.getInt("totalQty"),rent=x.getInt("rent"))
        }
        if (missing.isNotEmpty()) db.stockDao().insertAll(missing)
    }

    @Composable
    fun AppUI() {
        val customers by db.customerDao().all().collectAsState(emptyList())
        val stocks by db.stockDao().all().collectAsState(emptyList())
        val bookings by db.bookingDao().all().collectAsState(emptyList())
        var tab by remember { mutableStateOf(0) }
        val titles = listOf("ڈیش بورڈ","گاہک","نیا بکنگ","اسٹاک","واپسی","رپورٹس","بیک اپ")
        CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
            Scaffold(bottomBar = {
                NavigationBar { titles.forEachIndexed { i,t -> NavigationBarItem(selected=tab==i,onClick={tab=i},icon={},label={Text(t)}) } }
            }) { pad ->
                Column(Modifier.padding(pad).fillMaxSize()) {
                    Text("مدینہ ٹینٹ سروس 77/F", style=MaterialTheme.typography.headlineSmall, modifier=Modifier.fillMaxWidth().padding(16.dp), textAlign=TextAlign.Center)
                    when(tab) {
                        0 -> Dashboard(customers.size, bookings.size, stocks)
                        1 -> Customers(customers)
                        2 -> NewBooking(customers, stocks) { tab=1 }
                        3 -> StockPage(stocks, bookings)
                        4 -> ReturnPage(bookings, customers, stocks)
                        5 -> Reports(bookings, customers)
                        6 -> BackupPage()
                    }
                }
            }
        }
    }

    @Composable fun Dashboard(c:Int,b:Int,s:List<Stock>) {
        Column(Modifier.padding(16.dp)) {
            Text("گاہک: $c"); Text("کل بکنگ: $b"); Spacer(Modifier.height(12.dp))
            Text("اسٹاک کی موجودہ دستیابی", style=MaterialTheme.typography.titleMedium)
            s.forEach { Text("${it.name}: کل ${it.totalQty}") }
        }
    }


    @Composable fun Customers(list:List<Customer>) {
        var name by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }; var address by remember { mutableStateOf("") }
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(name,{name=it},label={Text("نام")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(phone,{phone=it},label={Text("فون")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(address,{address=it},label={Text("پتہ")},modifier=Modifier.fillMaxWidth())
            Button(onClick={ if(name.isNotBlank()) lifecycleScope.launch { db.customerDao().insert(Customer(name=name,phone=phone,address=address)); name="";phone="";address="" }},modifier=Modifier.fillMaxWidth()) { Text("گاہک محفوظ کریں") }
            LazyColumn { items(list) { Text("${it.name} — ${it.phone}",Modifier.padding(8.dp)) } }
        }
    }

    @Composable fun NewBooking(customers:List<Customer>, stocks:List<Stock>, done:()->Unit) {
        var customerId by remember { mutableStateOf<Long?>(null) }; var advance by remember { mutableStateOf("") }
        val qtys = remember { mutableStateMapOf<Long,Int>() }
        val total = stocks.sumOf { (qtys[it.id] ?: 0) * it.rent }
        Column(Modifier.padding(16.dp)) {
            Text("گاہک منتخب کریں")
            customers.forEach { c -> Button(onClick={customerId=c.id},modifier=Modifier.fillMaxWidth()) { Text(if(customerId==c.id) "✓ ${c.name}" else c.name) } }
            stocks.forEach { s ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("${s.name} (${s.rent})")
                    Row {
                        Button(onClick={ qtys[s.id]=maxOf(0,(qtys[s.id]?:0)-1) }){Text("-")}
                        Text("${qtys[s.id]?:0}",Modifier.padding(10.dp))
                        Button(onClick={ qtys[s.id]=minOf(s.totalQty,(qtys[s.id]?:0)+1) }){Text("+")}
                    }
                }
            }
            Text("کل کرایہ: $total")
            OutlinedTextField(advance,{advance=it.filter(Char::isDigit)},label={Text("ایڈوانس")},modifier=Modifier.fillMaxWidth())
            Button(onClick={
                if(customerId!=null) lifecycleScope.launch {
                    val adv=advance.toIntOrNull()?:0
                    val bId=db.bookingDao().insert(Booking(customerId=customerId!!,date=today(),total=total,advance=adv))
                    val items=stocks.mapNotNull { s -> val q=qtys[s.id]?:0; if(q>0) BookingItem(bookingId=bId,stockId=s.id,qty=q,rent=s.rent) else null }
                    db.bookingItemDao().insertAll(items)
                    done()
                }
            },modifier=Modifier.fillMaxWidth()) { Text("بکنگ محفوظ کریں") }
        }
    }

    @Composable fun StockPage(stocks:List<Stock>, bookings:List<Booking>) {
        var bis by remember { mutableStateOf<List<BookingItem>>(emptyList()) }
        var ris by remember { mutableStateOf<List<ReturnItem>>(emptyList()) }
        LaunchedEffect(Unit) {
            bis=db.bookingItemDao().allOnce()
            ris=db.returnDao().allOnce()
        }
        LazyColumn(Modifier.padding(16.dp)) {
            items(stocks) { s ->
                val issued = bis.filter{it.stockId==s.id}.sumOf{it.qty}
                val returned = ris.filter{it.stockId==s.id}.sumOf{it.returnedQty}
                val outstanding = maxOf(0, issued-returned)
                val available = maxOf(0, s.totalQty-outstanding)
                val lost = ris.filter{it.stockId==s.id}.sumOf{it.missingQty+it.damagedQty}
                Text("${s.name} | کل ${s.totalQty} | دستیاب ${available} | باہر ${outstanding} | گم/خراب ${lost}",Modifier.padding(6.dp))
            }
        }
    }

    @Composable fun ReturnPage(bookings:List<Booking>, customers:List<Customer>, stocks:List<Stock>) {
        var selected by remember { mutableStateOf<Long?>(null) }
        var items by remember { mutableStateOf<List<BookingItem>>(emptyList()) }
        val itemsState = remember { mutableStateMapOf<Long,Triple<Int,Int,Int>>() }
        LaunchedEffect(selected) {
            if (selected != null) items=db.bookingItemDao().byBooking(selected!!)
        }
        Column(Modifier.padding(16.dp)) {
            Text("واپسی درج کریں")
            bookings.forEach { b ->
                val c=customers.firstOrNull{it.id==b.customerId}
                Button(onClick={selected=b.id},modifier=Modifier.fillMaxWidth()){Text("${b.id} — ${c?.name ?: ""} — ${b.date}")}
            }
            items.forEach { bi ->
                val s=stocks.firstOrNull{it.id==bi.stockId}; val cur=itemsState[bi.id]?:Triple(0,0,0)
                Text("${s?.name} — دی گئی ${bi.qty}")
                OutlinedTextField(cur.first.toString(),{itemsState[bi.id]=Triple(it.toIntOrNull()?:0,cur.second,cur.third)},label={Text("واپس")})
                OutlinedTextField(cur.second.toString(),{itemsState[bi.id]=Triple(cur.first,it.toIntOrNull()?:0,cur.third)},label={Text("گم")})
                OutlinedTextField(cur.third.toString(),{itemsState[bi.id]=Triple(cur.first,cur.second,it.toIntOrNull()?:0)},label={Text("خراب")})
            }
            if (selected != null) Button(onClick={ lifecycleScope.launch {
                val all=db.bookingItemDao().byBooking(selected!!)
                db.returnDao().insertAll(all.map { bi ->
                    val x=itemsState[bi.id]?:Triple(0,0,0)
                    val safeReturned=minOf(bi.qty,maxOf(0,x.first))
                    val safeMissing=minOf(bi.qty-safeReturned,maxOf(0,x.second))
                    val safeDamaged=minOf(bi.qty-safeReturned-safeMissing,maxOf(0,x.third))
                    ReturnItem(bookingId=selected!!,stockId=bi.stockId,returnedQty=safeReturned,missingQty=safeMissing,damagedQty=safeDamaged,date=today())
                })
                selected=null; items=emptyList(); itemsState.clear()
            },modifier=Modifier.fillMaxWidth()){Text("واپسی محفوظ کریں")}
        }
    }

    @Composable fun Reports(bookings:List<Booking>, customers:List<Customer>) {
        val todayTotal=bookings.filter{it.date==today()}.sumOf{it.total}
        val month= today().substring(0,7)
        val monthTotal=bookings.filter{it.date.startsWith(month)}.sumOf{it.total}
        Column(Modifier.padding(16.dp)) {
            Text("آج کی بکنگ: ${bookings.count{it.date==today()}}")
            Text("آج کی رقم: $todayTotal")
            Text("اس ماہ کی بکنگ: ${bookings.count{it.date.startsWith(month)}}")
            Text("اس ماہ کی کل رقم: $monthTotal")
            Text("کل بقایا (ایڈوانس کے بعد): ${bookings.sumOf{it.total-it.advance}}")
        }
    }

    @Composable fun BackupPage() {
        Column(Modifier.padding(16.dp)) {
            Text("ڈیٹا بیک اپ اور بحالی",style=MaterialTheme.typography.titleLarge)
            Button(onClick={lifecycleScope.launch{exportJson=makeBackup();createBackup.launch("madina_backup_${today()}.json")}},modifier=Modifier.fillMaxWidth()){Text("مکمل بیک اپ فائل بنائیں")}
            Button(onClick={openBackup.launch(arrayOf("application/json"))},modifier=Modifier.fillMaxWidth()){Text("بیک اپ بحال کریں")}
            Text("نوٹ: بحالی موجودہ ڈیٹا کو مٹانے کے بجائے محفوظ انداز میں صرف غیر موجود اسٹاک شامل کرتی ہے۔")
        }
    }
}

