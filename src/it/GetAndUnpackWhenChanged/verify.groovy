def f = new File(basedir, "unpacked/hello.txt")
assert f.text == "Hello, world!"
def log = new File(basedir, "build.log")
assert log.exists() : "$log does not exist"
assert log.text =~ /Skipping unpacking as the file has not changed/ : "$log does not contain unpack skip message"
assert log.text =~ /Unpacking even though unchanged cache file exists/ : "$log does not contain force unpack message"
