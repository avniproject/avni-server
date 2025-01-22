package org.avni.ruleServer;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

public class Main {
    public static void main(String[] args) {
        String who = args.length == 0 ? "World" : args[0];
        System.out.println("Hello " + who + " from Java");

        try (Context context = Context.create()) {
            String jsCode = "(function myFun(param) { console.log('Hello ' + param + ' from JS'); })";
            Value function = context.eval("js", jsCode);
            function.execute(who);
        }
    }
}
