import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri

object WhatsAppHelper {

    private const val WHATSAPP_PACKAGE = "com.whatsapp"
    private const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"

//    fun openChat(context: Context, phoneNumber: String) {
//        val cleaned = phoneNumber
//            .replace(" ", "")
//            .replace("+", "")
//            .replace("-", "")
//
//        when {
//            isAppInstalled(context, WHATSAPP_PACKAGE) ->
//                launchWhatsApp(context, cleaned, WHATSAPP_PACKAGE)
//            isAppInstalled(context, WHATSAPP_BUSINESS_PACKAGE) ->
//                launchWhatsApp(context, cleaned, WHATSAPP_BUSINESS_PACKAGE)
//            else -> openInBrowser(context, cleaned)
//        }
//    }

    fun openChat(context: Context, phoneNumber: String) {
        val cleaned = phoneNumber
            .replace(" ", "")
            .replace("+", "")
            .replace("-", "")

        val withCountryCode = if (cleaned.length == 10) "91$cleaned" else cleaned

        val intents = listOf(WHATSAPP_PACKAGE, WHATSAPP_BUSINESS_PACKAGE)
            .filter { isAppInstalled(context, it) }
            .map { pkg ->
                Intent(Intent.ACTION_VIEW).apply {
                    data = "https://wa.me/$withCountryCode".toUri()
                    setPackage(pkg)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            }

        when {
            intents.isEmpty() -> openInBrowser(context, cleaned)
            intents.size == 1 -> context.startActivity(intents.first())
            else -> {
                val chooser = Intent.createChooser(intents.first(), "Open with").apply {
                    putExtra(Intent.EXTRA_INITIAL_INTENTS, intents.drop(1).toTypedArray())
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(chooser)
            }
        }
    }

    private fun launchWhatsApp(context: Context, cleanedNumber: String, packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://wa.me/$cleanedNumber")
                setPackage(packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            openInBrowser(context, cleanedNumber)
        }
    }

    private fun openInBrowser(context: Context, cleanedNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://wa.me/$cleanedNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "WhatsApp is not installed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
}