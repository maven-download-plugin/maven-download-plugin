File f = new File(basedir, "target/test.jpg")

assert f.exists() : "File $f.absolutePath does not exist"
assert f.length() > 0 : "File is empty"

def buildLogText = new File(basedir, 'build.log').text
def matcher = buildLogText =~/\Qmaven-download-plugin:wget skipped (not project root)\E/

assert matcher.find() : "Project 1 skip"
assert matcher.find() : "Project 2 skip"
assert matcher.find() : "Project 3 skip"
assert matcher.find() : "Project 4 skip"
assert !matcher.find()  : "No more skipps"
