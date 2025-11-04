package com.example.callyn

data class AppContact(
    val name: String,
    val number: String,
    val type: String = "default"
)

object ContactRepository {
    private val contacts = listOf(
        AppContact("Mom", "1234567890", "default"),
        AppContact("Work", "0987654321", "work"),
        AppContact("Akash Jha", "9619279579", "work"),
        AppContact("Mayank", "8218559151", "default"),
        AppContact("Alice", "5551234", "work"),
        AppContact("Aayushman", "+919304504962", "work"),
        AppContact("Nikhil", "5550101", "work"),
        AppContact("Riya", "5550102", "work"),
        AppContact("Nikhil", "5550103", "work"),
        AppContact("Riya", "5550104", "work"),
    )

    fun getAllContacts(): List<AppContact> = contacts

    private val contactMap by lazy {
        contacts.associate { it.number to it.name }
    }

    fun getNameByNumber(number: String): String? {
        return contactMap[number]
    }
}