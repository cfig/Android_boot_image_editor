package avb

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import org.bouncycastle.util.encoders.Hex

class ByteArraySerializer: JsonSerializer<ByteArray>() {
    override fun serialize(value: ByteArray?, gen: JsonGenerator?, serializers: SerializerProvider?) {
        if (value != null) {
            gen!!.writeString(String(Hex.encode(value!!)))
        } else {
            gen!!.writeString("")
        }
    }
}