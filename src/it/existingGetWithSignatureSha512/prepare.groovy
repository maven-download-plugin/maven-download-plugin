File target = new File(basedir, "target/")
if (!target.exists()) {
  target.mkdirs()
  assert target.exists() : "Folder $target.absolutePath must exist"
}

File f = new File(basedir, "target/687474703a2f2f696d672e71756c6963652e636f6d2f6c6f676f2d6269672e706e67")
if (f.exists()) {
  f.delete()
}
f.text = "hello"

assert f.exists() : "File $f.absolutePath does not exist"
assert f.length() > 0 : "File is empty"
