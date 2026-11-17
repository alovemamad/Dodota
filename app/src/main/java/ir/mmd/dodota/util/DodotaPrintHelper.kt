package ir.mmd.dodota.util

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import ir.mmd.dodota.R
import ir.mmd.dodota.data.model.AppCurrency
import ir.mmd.dodota.data.model.AppFontFamilyOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

enum class PrintTone {
    NEUTRAL,
    POSITIVE,
    NEGATIVE,
    INFO,
    WARNING
}

data class PrintKpiItem(
    val label: String,
    val value: String,
    val tone: PrintTone = PrintTone.NEUTRAL
)

data class PrintCell(
    val text: String,
    val isNumericOrDate: Boolean = false,
    val isBold: Boolean = false,
    val tone: PrintTone = PrintTone.NEUTRAL
)

data class PrintSectionBlock(
    val title: String,
    val badge: String = "",
    val headers: List<String>,
    val rows: List<List<PrintCell>>,
    val footerSummary: String = "",
    val emptyMessage: String = "موردی برای نمایش در این بخش وجود ندارد."
)

data class PrintableReportDocument(
    val documentTitle: String,
    val subtitle: String = "",
    val dateRangeLabel: String = "همه تاریخ‌ها",
    val currency: AppCurrency = AppCurrency.TOMAN,
    val fontFamily: AppFontFamilyOption = AppFontFamilyOption.VAZIRMATN,
    val showFrame: Boolean = true,
    val showSummary: Boolean = true,
    val kpis: List<PrintKpiItem> = emptyList(),
    val sections: List<PrintSectionBlock> = emptyList(),
    val footerNote: String = ""
)

object DodotaPrintHelper {

    private val cachedFontBase64 = ConcurrentHashMap<AppFontFamilyOption, String>()
    private val activePrintWebViews = mutableSetOf<WebView>()

    fun printDocument(context: Context, document: PrintableReportDocument) {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager == null) {
                Toast.makeText(context, "سرویس چاپ روی این دستگاه در دسترس نیست.", Toast.LENGTH_LONG).show()
                return
            }

            val html = buildFramedHtml(context, document)
            val webView = WebView(context).apply {
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.loadsImagesAutomatically = false
            }
            activePrintWebViews.add(webView)

            val jobName = "دودوتا - ${document.documentTitle}"
            webView.webViewClient = object : WebViewClient() {
                private var printDispatched = false

                override fun onPageFinished(view: WebView?, url: String?) {
                    if (printDispatched || view == null) return
                    printDispatched = true
                    try {
                        val baseAdapter = view.createPrintDocumentAdapter(jobName)
                        val lifecycleAdapter = CleanupPrintDocumentAdapter(baseAdapter) {
                            view.post {
                                activePrintWebViews.remove(view)
                                try {
                                    view.destroy()
                                } catch (_: Exception) {
                                }
                            }
                        }
                        val attributes = PrintAttributes.Builder()
                            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                            .build()
                        printManager.print(jobName, lifecycleAdapter, attributes)
                    } catch (_: Exception) {
                        activePrintWebViews.remove(view)
                        try {
                            view.destroy()
                        } catch (_: Exception) {
                        }
                        Toast.makeText(context, "خطا در آماده‌سازی فایل چاپ.", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            webView.loadDataWithBaseURL(null, html, "text/html; charset=UTF-8", "UTF-8", null)
        } catch (_: Exception) {
            Toast.makeText(context, "امکان باز کردن صفحه چاپ وجود ندارد.", Toast.LENGTH_SHORT).show()
        }
    }

    private class CleanupPrintDocumentAdapter(
        private val delegate: PrintDocumentAdapter,
        private val onFinished: () -> Unit
    ) : PrintDocumentAdapter() {

        override fun onStart() {
            delegate.onStart()
        }

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes?,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback?,
            extras: Bundle?
        ) {
            delegate.onLayout(oldAttributes, newAttributes, cancellationSignal, callback, extras)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor?,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback?
        ) {
            delegate.onWrite(pages, destination, cancellationSignal, callback)
        }

        override fun onFinish() {
            try {
                delegate.onFinish()
            } finally {
                onFinished()
            }
        }
    }

    private fun loadFontBase64(context: Context, option: AppFontFamilyOption): String {
        cachedFontBase64[option]?.let { return it }
        val resId = when (option) {
            AppFontFamilyOption.VAZIRMATN -> R.font.vazirmatn
            AppFontFamilyOption.CAIRO -> R.font.cairo
            AppFontFamilyOption.ALMARAI -> R.font.almarai
            AppFontFamilyOption.RUBIK -> R.font.rubik
            AppFontFamilyOption.LALEZAR -> R.font.lalezar
            AppFontFamilyOption.MARKAZI -> R.font.markazi_text
            AppFontFamilyOption.MIRZA -> R.font.mirza
            AppFontFamilyOption.NOTO_NASKH -> R.font.noto_naskh_arabic
        }
        return try {
            val bytes = context.resources.openRawResource(resId).use { it.readBytes() }
            val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
            cachedFontBase64[option] = encoded
            encoded
        } catch (_: Exception) {
            ""
        }
    }

    private fun escapeHtml(raw: String): String {
        if (raw.isEmpty()) return ""
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&#39;")
                '\n' -> sb.append("<br/>")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun toneClass(tone: PrintTone): String = when (tone) {
        PrintTone.POSITIVE -> "tone-positive"
        PrintTone.NEGATIVE -> "tone-negative"
        PrintTone.INFO -> "tone-info"
        PrintTone.WARNING -> "tone-warning"
        PrintTone.NEUTRAL -> "tone-neutral"
    }

    private fun buildFramedHtml(context: Context, doc: PrintableReportDocument): String {
        val nowMillis = System.currentTimeMillis()
        val isEn = doc.currency.isEnglishDigits
        val printedDate = PersianDateHelper.formatToPersianDate(nowMillis, isEn)
        val rawTime = SimpleDateFormat("HH:mm", Locale.US).format(Date(nowMillis))
        val printedTime = PersianNumberFormatter.formatDigits(rawTime, isEn)
        val fontBase64 = loadFontBase64(context, doc.fontFamily)

        val fontFaceCss = if (fontBase64.isNotEmpty()) {
            """
            @font-face {
                font-family: 'DodotaPrintFont';
                src: url(data:font/ttf;base64,$fontBase64) format('truetype');
                font-weight: 100 900;
                font-style: normal;
            }
            """.trimIndent()
        } else ""

        val frameOverlayHtml = if (doc.showFrame) {
            """
            <div class="page-frame-outer"></div>
            <div class="page-frame-inner"></div>
            """.trimIndent()
        } else ""

        val kpiHtml = if (doc.showSummary && doc.kpis.isNotEmpty()) {
            val cols = doc.kpis.joinToString("\n") { kpi ->
                """
                <div class="kpi-card ${toneClass(kpi.tone)}">
                    <div class="kpi-label">${escapeHtml(kpi.label)}</div>
                    <div class="kpi-value">${escapeHtml(kpi.value)}</div>
                </div>
                """.trimIndent()
            }
            """
            <div class="kpi-grid">
                $cols
            </div>
            """.trimIndent()
        } else ""

        val sectionsHtml = doc.sections.mapIndexed { index, sec ->
            val sectionNum = PersianNumberFormatter.formatDigits(index + 1, isEn)
            val badgeHtml = if (sec.badge.isNotBlank()) {
                """<span class="section-badge">${escapeHtml(sec.badge)}</span>"""
            } else ""

            val tableOrEmpty = if (sec.rows.isEmpty()) {
                """<div class="empty-box">${escapeHtml(sec.emptyMessage)}</div>"""
            } else {
                val thHtml = sec.headers.joinToString("") { h ->
                    "<th>${escapeHtml(h)}</th>"
                }
                val trHtml = sec.rows.joinToString("\n") { row ->
                    val tds = row.joinToString("") { cell ->
                        val classes = buildList {
                            add(toneClass(cell.tone))
                            if (cell.isBold) add("cell-bold")
                            if (cell.isNumericOrDate) add("cell-num")
                        }.joinToString(" ")
                        val dirAttr = if (cell.isNumericOrDate) " dir=\"ltr\"" else ""
                        "<td class=\"$classes\"><span$dirAttr>${escapeHtml(cell.text)}</span></td>"
                    }
                    "<tr>$tds</tr>"
                }
                val footerRow = if (sec.footerSummary.isNotBlank()) {
                    """
                    <div class="section-footer">${escapeHtml(sec.footerSummary)}</div>
                    """.trimIndent()
                } else ""

                """
                <table class="data-table">
                    <thead>
                        <tr>$thHtml</tr>
                    </thead>
                    <tbody>
                        $trHtml
                    </tbody>
                </table>
                $footerRow
                """.trimIndent()
            }

            """
            <section class="report-section">
                <div class="section-header">
                    <div class="section-title-wrap">
                        <span class="section-num">$sectionNum</span>
                        <span class="section-title">${escapeHtml(sec.title)}</span>
                    </div>
                    $badgeHtml
                </div>
                $tableOrEmpty
            </section>
            """.trimIndent()
        }.joinToString("\n")

        val extraNoteHtml = if (doc.footerNote.isNotBlank()) {
            """<div class="extra-note">${escapeHtml(doc.footerNote)}</div>"""
        } else ""

        return """
            <!DOCTYPE html>
            <html lang="fa" dir="rtl">
            <head>
                <meta charset="UTF-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                <style>
                    $fontFaceCss
                    @page {
                        size: A4 portrait;
                        margin: 10mm;
                    }
                    * {
                        box-sizing: border-box;
                        -webkit-print-color-adjust: exact !important;
                        print-color-adjust: exact !important;
                    }
                    html, body {
                        margin: 0;
                        padding: 0;
                        background: #FFFFFF;
                        color: #0F172A;
                        font-family: 'DodotaPrintFont', Tahoma, sans-serif;
                        font-size: 11px;
                        line-height: 1.55;
                        direction: rtl;
                    }
                    .page-frame-outer {
                        position: fixed;
                        top: 0;
                        left: 0;
                        right: 0;
                        bottom: 0;
                        border: 2px solid #1E293B;
                        border-radius: 6px;
                        pointer-events: none;
                        z-index: 9999;
                    }
                    .page-frame-inner {
                        position: fixed;
                        top: 4px;
                        left: 4px;
                        right: 4px;
                        bottom: 4px;
                        border: 0.75px solid #94A3B8;
                        border-radius: 4px;
                        pointer-events: none;
                        z-index: 9999;
                    }
                    table.print-shell {
                        width: 100%;
                        border-collapse: collapse;
                        border: none;
                    }
                    table.print-shell > thead > tr > td,
                    table.print-shell > tfoot > tr > td,
                    table.print-shell > tbody > tr > td {
                        border: none;
                        padding: 0;
                    }
                    .frame-top-spacer {
                        height: 12px;
                    }
                    .frame-body-cell {
                        padding: 2px 16px 6px 16px !important;
                        vertical-align: top;
                    }
                    .frame-bottom-bar {
                        padding: 6px 16px 10px 16px;
                        border-top: 1px dashed #CBD5E1;
                        margin: 6px 14px 0 14px;
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        font-size: 9px;
                        color: #64748B;
                    }
                    .doc-header {
                        border: 1.5px solid #1E293B;
                        border-radius: 8px;
                        background: #F8FAFC;
                        padding: 12px 14px;
                        margin-bottom: 12px;
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        gap: 12px;
                        page-break-inside: avoid;
                    }
                    .doc-header-right {
                        flex: 1;
                    }
                    .brand-row {
                        display: flex;
                        align-items: center;
                        gap: 8px;
                        margin-bottom: 4px;
                    }
                    .brand-pill {
                        background: #0F172A;
                        color: #FFFFFF;
                        font-weight: 800;
                        font-size: 10px;
                        padding: 2px 8px;
                        border-radius: 4px;
                        letter-spacing: 0.2px;
                    }
                    .doc-title {
                        font-size: 15px;
                        font-weight: 800;
                        color: #0F172A;
                        margin: 0;
                    }
                    .doc-subtitle {
                        font-size: 10.5px;
                        color: #475569;
                        margin-top: 2px;
                    }
                    .doc-meta-box {
                        border: 1px solid #CBD5E1;
                        background: #FFFFFF;
                        border-radius: 6px;
                        padding: 6px 10px;
                        min-width: 170px;
                        font-size: 9.5px;
                        color: #334155;
                    }
                    .meta-line {
                        display: flex;
                        justify-content: space-between;
                        gap: 10px;
                        padding: 1.5px 0;
                    }
                    .meta-label {
                        color: #64748B;
                    }
                    .meta-val {
                        font-weight: 700;
                        color: #0F172A;
                    }
                    .kpi-grid {
                        display: flex;
                        flex-wrap: wrap;
                        gap: 8px;
                        margin-bottom: 14px;
                        page-break-inside: avoid;
                    }
                    .kpi-card {
                        flex: 1 1 130px;
                        border: 1px solid #CBD5E1;
                        border-top: 3px solid #334155;
                        border-radius: 6px;
                        padding: 8px 10px;
                        background: #FFFFFF;
                    }
                    .kpi-card.tone-positive { border-top-color: #15803D; background: #F0FDF4; }
                    .kpi-card.tone-negative { border-top-color: #B91C1C; background: #FEF2F2; }
                    .kpi-card.tone-info { border-top-color: #1D4ED8; background: #EFF6FF; }
                    .kpi-card.tone-warning { border-top-color: #B45309; background: #FFFBEB; }
                    .kpi-label {
                        font-size: 9.5px;
                        color: #475569;
                        margin-bottom: 3px;
                    }
                    .kpi-value {
                        font-size: 12px;
                        font-weight: 800;
                        color: #0F172A;
                    }
                    .report-section {
                        margin-bottom: 14px;
                        border: 1px solid #CBD5E1;
                        border-radius: 7px;
                        overflow: hidden;
                        background: #FFFFFF;
                    }
                    .section-header {
                        background: #F1F5F9;
                        border-bottom: 1.5px solid #94A3B8;
                        padding: 7px 11px;
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        page-break-after: avoid;
                    }
                    .section-title-wrap {
                        display: flex;
                        align-items: center;
                        gap: 7px;
                    }
                    .section-num {
                        display: inline-block;
                        min-width: 18px;
                        height: 18px;
                        line-height: 18px;
                        text-align: center;
                        background: #1E293B;
                        color: #FFFFFF;
                        border-radius: 4px;
                        font-size: 10px;
                        font-weight: 700;
                    }
                    .section-title {
                        font-size: 12px;
                        font-weight: 800;
                        color: #0F172A;
                    }
                    .section-badge {
                        background: #FFFFFF;
                        border: 1px solid #CBD5E1;
                        color: #334155;
                        font-size: 9.5px;
                        font-weight: 700;
                        padding: 2px 8px;
                        border-radius: 999px;
                    }
                    table.data-table {
                        width: 100%;
                        border-collapse: collapse;
                        font-size: 10px;
                    }
                    table.data-table thead {
                        display: table-header-group;
                    }
                    table.data-table th {
                        background: #F8FAFC;
                        color: #1E293B;
                        font-weight: 800;
                        text-align: right;
                        padding: 6px 8px;
                        border-bottom: 1.5px solid #CBD5E1;
                        border-left: 1px solid #E2E8F0;
                    }
                    table.data-table th:last-child {
                        border-left: none;
                    }
                    table.data-table td {
                        padding: 6px 8px;
                        border-bottom: 1px solid #E2E8F0;
                        border-left: 1px solid #F1F5F9;
                        vertical-align: middle;
                        word-break: break-word;
                    }
                    table.data-table td:last-child {
                        border-left: none;
                    }
                    table.data-table tbody tr:nth-child(even) {
                        background: #FAFAFA;
                    }
                    table.data-table tr {
                        page-break-inside: avoid;
                    }
                    .cell-bold { font-weight: 700; }
                    .cell-num { font-variant-numeric: tabular-nums; white-space: nowrap; }
                    .tone-positive { color: #15803D; font-weight: 700; }
                    .tone-negative { color: #B91C1C; font-weight: 700; }
                    .tone-info { color: #1D4ED8; font-weight: 700; }
                    .tone-warning { color: #B45309; font-weight: 700; }
                    .tone-neutral { color: #0F172A; }
                    .section-footer {
                        background: #F8FAFC;
                        border-top: 1px solid #CBD5E1;
                        padding: 6px 10px;
                        font-size: 10px;
                        font-weight: 700;
                        color: #1E293B;
                        text-align: left;
                    }
                    .empty-box {
                        padding: 14px;
                        text-align: center;
                        color: #64748B;
                        font-size: 10.5px;
                    }
                    .extra-note {
                        border: 1px dashed #94A3B8;
                        border-radius: 6px;
                        padding: 8px 10px;
                        font-size: 9.5px;
                        color: #475569;
                        margin-top: 6px;
                        page-break-inside: avoid;
                    }
                </style>
            </head>
            <body>
                $frameOverlayHtml
                <table class="print-shell">
                    <thead>
                        <tr><td><div class="frame-top-spacer"></div></td></tr>
                    </thead>
                    <tfoot>
                        <tr>
                            <td>
                                <div class="frame-bottom-bar">
                                    <span>گزارش رسمی اپلیکیشن مدیریت مالی دودوتا</span>
                                    <span>تاریخ چاپ: $printedDate • ساعت $printedTime</span>
                                </div>
                            </td>
                        </tr>
                    </tfoot>
                    <tbody>
                        <tr>
                            <td class="frame-body-cell">
                                <header class="doc-header">
                                    <div class="doc-header-right">
                                        <div class="brand-row">
                                            <span class="brand-pill">دودوتا</span>
                                            <h1 class="doc-title">${escapeHtml(doc.documentTitle)}</h1>
                                        </div>
                                        ${if (doc.subtitle.isNotBlank()) "<div class=\"doc-subtitle\">${escapeHtml(doc.subtitle)}</div>" else ""}
                                    </div>
                                    <div class="doc-meta-box">
                                        <div class="meta-line">
                                            <span class="meta-label">بازه گزارش:</span>
                                            <span class="meta-val">${escapeHtml(doc.dateRangeLabel)}</span>
                                        </div>
                                        <div class="meta-line">
                                            <span class="meta-label">تاریخ چاپ:</span>
                                            <span class="meta-val">$printedDate</span>
                                        </div>
                                        <div class="meta-line">
                                            <span class="meta-label">واحد مالی:</span>
                                            <span class="meta-val">${escapeHtml(doc.currency.unitName)}</span>
                                        </div>
                                    </div>
                                </header>
                                $kpiHtml
                                $sectionsHtml
                                $extraNoteHtml
                            </td>
                        </tr>
                    </tbody>
                </table>
            </body>
            </html>
        """.trimIndent()
    }
}
