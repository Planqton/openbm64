package at.plankt0n.openbm64.db

data class Measurement(
    val id: Long = 0,
    val timestamp: String,
    val systole: Int,
    val diastole: Int,
    val map: Double,
    val pulse: Int?,
    val raw: ByteArray,
    var info: String? = null,
    var invalid: Boolean = false
)
