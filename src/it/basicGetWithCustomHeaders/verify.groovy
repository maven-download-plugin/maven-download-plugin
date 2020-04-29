File f = new File(basedir, "target/http-request-headers")
assert f.exists() : "File $f.absolutePath does not exist"
assert f.length() > 0 : "File is empty"
assert f.text.contains("this is the first custom header") : "First custom header missing"
assert f.text.contains("this is the second custom header") : "Second custom header missing"
