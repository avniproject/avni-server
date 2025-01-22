package org.avni.ruleServer;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.IOException;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) throws IOException {
        String jsFilePath = "build/resources/js/main.js";

        try (Context context = Context.newBuilder("js")
                .allowAllAccess(true) // Allow access to file system if needed
                .option("js.esm-eval-returns-exports", "true") // Enable ECMAScript modules
                .build()) {

            // Read and execute the main JavaScript file
            Source source = Source.newBuilder("js", Paths.get(jsFilePath).toFile())
                    .mimeType("application/javascript+module")
                    .build();
            context.eval(source);

            // Call a function from the JavaScript code (optional)
            Value result = context.eval(source);
            System.out.println(result);
        }
    }
}
