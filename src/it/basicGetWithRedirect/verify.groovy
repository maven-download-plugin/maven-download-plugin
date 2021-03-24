File f = new File(basedir, "target/version.txt")
assert f.exists() : "File $f.absolutePath does not exist"
assert f.length() > 0 : "File is empty"
