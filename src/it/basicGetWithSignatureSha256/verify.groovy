File f = new File(basedir, "target/lolcatsdotcomcm90ebvhwphtzqvf.jpg")
assert f.exists() : "File $f.absolutePath does not exist"
assert f.length() > 0 : "File is empty"
