File target = new File(basedir, "target/")
if (!target.exists()) {
  target.mkdirs()
  assert target.exists() : "Folder $target.absolutePath must exist"
}

File f = new File(basedir, "target/lolcatsdotcomcm90ebvhwphtzqvf.jpg")
if (f.exists()) {
  f.delete()
}
f.text = "hello"

assert f.exists() : "File $f.absolutePath does not exist"
assert f.length() > 0 : "File is empty"
