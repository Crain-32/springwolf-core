package io.github.stavshamir.springwolf.asyncapi.serializers;


import com.asyncapi.v2.binding.kafka.KafkaOperationBinding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

public class KafkaOperationBindingSerializer extends StdSerializer<KafkaOperationBinding> {

    public KafkaOperationBindingSerializer() {
        this(null);
    }

    public KafkaOperationBindingSerializer(Class<KafkaOperationBinding> t) {
        super(t);
    }

    @Override
    public void serialize(KafkaOperationBinding value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeStartObject();

        if (value.getGroupId() != null) {
            writeGroupId(value, gen);
        }

        gen.writeEndObject();
    }

    private void writeGroupId(KafkaOperationBinding value, JsonGenerator gen) throws IOException {
        gen.writeFieldName("groupId");
        gen.writeStartObject();
        gen.writeStringField("type", "string");
        gen.writeFieldName("enum");
        gen.writeStartArray();
        gen.writeString((String) value.getGroupId());
        gen.writeEndArray();
        gen.writeEndObject();
    }

}
