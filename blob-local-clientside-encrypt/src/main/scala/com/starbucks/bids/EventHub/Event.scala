package com.starbucks.bids.EventHub

/**
  * Case class representing an event in the Event hub.
  * @param uri
  * @param sharedAccessSignatureToken
  */
class Event(uri: String, sharedAccessSignatureToken: String) {
  def toJson: String =
    s"""{
       |    "uri": $uri,
       |    "sharedAccessSignatureToken": $sharedAccessSignatureToken
       |}
     """.stripMargin

  def getUri = this.uri
  def getSASToken = this.sharedAccessSignatureToken
}
