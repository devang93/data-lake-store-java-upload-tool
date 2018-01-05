package com.starbucks.bids

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

/**
  * Created by depatel on 1/3/18.
  */
class BlobUploadOutputStream extends ByteArrayOutputStream{

  def toInputStream: ByteArrayInputStream = new ByteArrayInputStream(buf, 0, count)
}
