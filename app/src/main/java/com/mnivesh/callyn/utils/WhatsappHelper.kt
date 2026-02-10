import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri

object WhatsAppHelper {

    fun openChat(context: Context, phoneNumber: String) {
        try {
            // Clean number (remove spaces, +, etc)
            val cleanedNumber = phoneNumber
                .replace(" ", "")
                .replace("+", "")
                .replace("-", "")

            val url = "https://wa.me/$cleanedNumber" // India code (91)

            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = url.toUri()
                setPackage("com.whatsapp")
            }

            context.startActivity(intent)

        } catch (e: Exception) {
            Toast.makeText(
                context,
                "WhatsApp not installed",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}