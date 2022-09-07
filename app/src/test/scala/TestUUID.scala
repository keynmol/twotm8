package twotm8

object TestUUID extends verify.BasicTestSuite:
  test("Random UUID generation") {
    val uuid1 = generateRandomUUID()
    assert(4 == uuid1.version())
    assert(2 == uuid1.variant())

    val uuid2 = generateRandomUUID()
    assert(2 == uuid2.variant())
    assert(4 == uuid2.version())

    assert(uuid1 != uuid2)
  }

