package com.universalimporter.infrastructure.persistence;

import com.universalimporter.domain.config.ConfigHasher;
import com.universalimporter.domain.config.ImportConfiguration;
import org.springframework.stereotype.Component;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256, as lowercase hex, of the configuration's storage documents (design D7, S7). */
@Component
public class JsonConfigHasher implements ConfigHasher {

    /**
     * Own mapper, as for storage: the hash must not change when the API's JSON settings do, or every processed
     * session would look changed.
     */
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    @Override
    public String hash(ImportConfiguration configuration) {
        byte[] json = JSON.writeValueAsBytes(new HashedContent(TargetSchemaDocument.from(configuration.schema()),
                MappingDocument.from(configuration.mapping()),
                TransformationsDocument.from(configuration.transformations())));
        return HexFormat.of().formatHex(sha256().digest(json));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java platform must support SHA-256", e);
        }
    }

    /**
     * The hashed documents, in a fixed order; F07 adds validations. Map entries (transformation params) are
     * sorted by key, so the order a client sent them in does not matter.
     */
    private record HashedContent(TargetSchemaDocument schema, MappingDocument mapping,
                                 TransformationsDocument transformations) {
    }
}
