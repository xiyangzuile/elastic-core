QUnit.module("nxt.address");

QUnit.test("nxtAddress", function (assert) {
    var address = new NxtAddress();
    assert.equal(address.set("XEL-XK4R-7VJU-6EQG-7R335"), true, "valid address");
    assert.equal(address.toString(), "XEL-XK4R-7VJU-6EQG-7R335", "address");
    assert.equal(address.set("XEL-XK4R-7VJU-6EQG-7R336"), false, "invalid address");
});
