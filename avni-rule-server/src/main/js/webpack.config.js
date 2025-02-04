const path = require('path');
const CopyWebpackPlugin = require('copy-webpack-plugin');

const config = {
    target: 'node',
    entry: {
        rules: "./ruleInvoker.js"
    },
    output: {
        filename: 'ruleInvoker.js',
        libraryTarget: 'umd',
        library: 'ruleInvoker',
        globalObject: 'this',
        path: path.resolve(__dirname, 'exports')
    },
    plugins: [
        new CopyWebpackPlugin({
            patterns: [
                {from: './package.json', to: 'package.json'},
                // {from: './package-lock.json', to: 'package-lock.json'}
            ]
        })
    ],
    module: {
        rules: [
            {
                test: /\.js$/,
                exclude: /(node_modules)/,
                use: {
                    loader: 'babel-loader',
                    options: {
                        "presets": ['@babel/preset-env'],
                        "plugins": [
                            "@babel/plugin-proposal-class-properties",
                            "@babel/plugin-syntax-export-extensions",
                            ["@babel/plugin-proposal-decorators", {"legacy": true}],
                            "@babel/plugin-transform-destructuring"
                        ]
                    }
                }
            }
        ]
    }
};

module.exports = config;
