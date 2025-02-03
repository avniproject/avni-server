export function mainFunction() {
    return {
        hello: function (foo, springDependencyObject) {
            console.log('Hello from JavaScript!', foo);
            const allNames = springDependencyObject.getAllNames();
            console.log(allNames);
        }
    }
}

mainFunction();
