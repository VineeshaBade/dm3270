package com.bytezone.dm3270;

/**
 * Interface to be invoked when there is some change on terminal connection.
 */
public interface ConnectionListener {

  /**
   * Method invoked when connection is established to terminal server.
   */
  void onConnection();

  /**
   * Method invoked when an {@link Exception} is thrown.
   *
   * @param ex Exception thrown while connected to the terminal server.
   */
  void onException(Exception ex);

  /**
   * Method invoked when connection is closed by terminal server.
   */
  void onConnectionClosed();

}
