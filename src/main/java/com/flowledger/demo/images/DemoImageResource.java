package com.flowledger.demo.images;

import java.io.IOException;
import java.io.InputStream;

public record DemoImageResource(String classpathLocation, String filename, String contentType) {
    public InputStream openStream() throws IOException {
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(classpathLocation);
        if (in == null) {
            in = DemoImageResource.class.getClassLoader().getResourceAsStream(classpathLocation);
        }
        if (in == null) {
            throw new IOException("Missing classpath resource: " + classpathLocation);
        }
        return in;
    }
}
