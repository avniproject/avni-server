import { greet } from './utils.js';

export function mainFunction() {
    const message = greet('GraalVM');
    console.log(message);
    return message;
}

mainFunction();
