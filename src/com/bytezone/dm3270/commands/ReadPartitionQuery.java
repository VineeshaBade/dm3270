package com.bytezone.dm3270.commands;

import com.bytezone.dm3270.display.Screen;
import com.bytezone.dm3270.structuredfields.StructuredField;

public class ReadPartitionQuery extends Command
{
  private final byte partitionID;
  private String typeName;

  public ReadPartitionQuery (byte[] buffer, int offset, int length)
  {
    super (buffer, offset, length);

    assert data[0] == StructuredField.READ_PARTITION;
    partitionID = data[1];
  }

  @Override
  public void process (Screen screen)
  {
    if (reply != null)
      return;

    switch (data[2])
    {
      case (byte) 0x02:
        reply = new ReadStructuredFieldCommand ();      // build a QueryReply
        typeName = "Read Partition (Query)";
        break;

      case (byte) 0x03:
        switch (data[3])
        {
          case 0:
            System.out.println ("QCode List not written yet");
            break;

          case 1:
            System.out.println ("Equivalent + QCode List not written yet");
            break;

          case 2:
            reply = new ReadStructuredFieldCommand ();      // build a QueryReply
            typeName = "Read Partition (QueryList)";
            break;

          default:
            System.out.printf ("Unknown query type: %02X%n", data[3]);
        }
        break;

      default:
        System.out.printf ("Unknown ReadStructuredField type: %02X%n", data[2]);
    }
  }

  @Override
  public String getName ()
  {
    return typeName;
  }

  @Override
  public String toString ()
  {
    StringBuilder text = new StringBuilder ();

    text.append (String.format ("%s", typeName));

    return text.toString ();
  }
}