File f = new File(basedir, "target/hello-1.0.pom")

assert !f.exists() : "The root project is NOT invoking the plugin, but the file $f.absolutePath does exist"

def buildLogText = new File(basedir, 'build.log').text
def matcher = buildLogText =~/\Qmaven-download-plugin:wget skipped (not project root)\E/

assert matcher.find() : "Project 1 skip"
assert matcher.find() : "Project 2 skip"
assert matcher.find() : "Project 3 skip"
assert matcher.find() : "Project 4 skip"
assert !matcher.find()  : "No more skips"
