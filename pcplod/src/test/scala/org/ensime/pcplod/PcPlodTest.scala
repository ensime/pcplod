// Copyright: 2010 - 2016 Rory Graves, Sam Halliday
// License: http://www.apache.org/licenses/LICENSE-2.0
package org.ensime.pcplod

import org.scalatest._
import Matchers._

class PcPlodTest extends FlatSpec {

  "Mr Plod" should "typecheck a compilable valid noddy file" in withMrPlod("/com/acme/foo.scala") { mr =>
    mr.symbolAtPoint('foo) shouldBe Some("com.acme.Foo")
    mr.typeAtPoint('foo) shouldBe Some("com.acme.Foo.type")

    // Right now this does not return the param name, it returns the type of the param.
    // this is the wrong assertion
    mr.symbolAtPoint('input_a) shouldBe Some("com.acme.Foo.bar")
    mr.typeAtPoint('input_a) shouldBe Some("String")

    //    mr.messages shouldBe 'empty
  }

  "Mr Plod" should "typecheck an uncompilable valid noddy file" in withMrPlod("/com/acme/foo_bad.scala") { mr =>
    // not entirely sure what the PC would do here...
    mr.typeAtPoint('foo) shouldBe Some("com.acme.Foo.type")
    //    mr.typeAtPoint('foo) shouldBe None

    // returns Some("notype>")
    mr.typeAtPoint('input_a) shouldBe None
    //    mr.typeAtPoint('foo) shouldBe None

    mr.messages should contain only ()
  }

}
