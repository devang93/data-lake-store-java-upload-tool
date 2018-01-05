package com.starbucks.bids.EventHub

import java.util.function.Consumer

import com.microsoft.azure.eventprocessorhost.ExceptionReceivedEventArgs
import org.slf4j.LoggerFactory

/**
  * Error Handler class for errors thrown by Event Processor Host.
  */
class EventHubErrorNotificationHandler extends Consumer[ExceptionReceivedEventArgs] {
  private val logger = LoggerFactory.getLogger("EventHubProcessorErrorHandler")

  override def accept(t: ExceptionReceivedEventArgs): Unit = {
    logger.error("Host "+ t.getHostname + " received general error notification during "+ t.getAction+ ": "+ t.getException.toString)
  }
}
