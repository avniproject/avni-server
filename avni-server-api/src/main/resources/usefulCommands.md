# Excluding Hidden Files from ZIP Archives on Mac

## Creating ZIP Archives Without Hidden Files

### Remove All Hidden Files
```bash
zip -r data.zip . -x ".*" -x "__MACOSX"
```
> **Warning**: Removes all dot-hidden files, including `.htaccess` and other useful files.

### Remove Only macOS Hidden Files
```bash
zip -r data.zip . -x ".DS_Store" -x "__MACOSX"
```

Both commands create `data.zip` in the current directory without the specified hidden files.

