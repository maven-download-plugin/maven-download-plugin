import groovy.json.JsonSlurper

File f = new File(basedir, "target/get")
assert f.exists() : "File $f.absolutePath does not exist"
assert f.length() > 0 : "File is empty"

def content = new JsonSlurper().parseText(f.text);
assert content.headers["x-custom-1"] == "first custom header"
assert content.headers["x-custom-2"] == "second custom header"
