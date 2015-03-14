package com.bytezone.dm3270.display;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.bytezone.dm3270.attributes.Attribute;

public class Cursor
{
  private static final boolean WITH_CURSOR = true;
  private static final boolean WITHOUT_CURSOR = false;

  private final Screen screen;

  private int currentPosition;
  private Field currentField;
  private boolean visible = false;    // this should match the keyboard locked status

  private final List<Attribute> unappliedAttributes = new ArrayList<> ();

  public enum Direction
  {
    LEFT, RIGHT, UP, DOWN
  }

  public Cursor (Screen screen)
  {
    this.screen = screen;
  }

  public void draw ()
  {
    screen.drawPosition (currentPosition, visible);
  }

  public void setVisible (boolean visible)
  {
    this.visible = visible;
    if (visible)
    {
      setCurrentField ();
      notifyCursorMove (0, currentPosition, currentField, 0);
    }
    else
      resetCurrentField ();
    draw ();
  }

  public ScreenPosition getScreenPosition ()
  {
    return screen.getScreenPosition (currentPosition);
  }

  public Field getCurrentField ()
  {
    if (currentField == null)
      setCurrentField ();
    return currentField;
  }

  public int getLocation ()
  {
    return currentPosition;
  }

  // ---------------------------------------------------------------------------------//
  // Update screen contents
  // ---------------------------------------------------------------------------------//

  // called from ConsoleKeyEvent when the user types
  public void typeChar (byte value)
  {
    if (currentField != null && currentField.isUnprotected ())
    {
      screen.getScreenPosition (currentPosition).setChar (value);
      currentField.setModified (true);

      int newPosition = screen.validate (currentPosition + 1);
      if (!currentField.contains (newPosition))
      {
        Field newField = currentField.getNextUnprotectedField ();
        newPosition = newField.getFirstLocation ();
      }
      moveTo (newPosition);
    }
    //    else
    //      System.out.println ("Can't type here");         // lock the keyboard?
  }

  public void backspace ()
  {
    if (currentField != null && currentField.isUnprotected ())
    {

      int first = currentField.getFirstLocation ();
      if (currentPosition != first)
      {
        int newPosition = screen.validate (currentPosition) - 1;
        screen.getScreenPosition (newPosition).setChar ((byte) 0x00);
        moveTo (newPosition);
      }
    }
  }

  // called from Orders when building the screen
  public void setChar (byte value)
  {
    ScreenPosition sp = screen.getScreenPosition (currentPosition);

    sp.reset ();
    if (unappliedAttributes.size () > 0)
      applyAttributes (sp);

    sp.setChar (value);
  }

  // called from Orders when building the screen
  public void setGraphicsChar (byte value)
  {
    ScreenPosition sp = screen.getScreenPosition (currentPosition);

    sp.reset ();
    if (unappliedAttributes.size () > 0)
      applyAttributes (sp);

    sp.setGraphicsChar (value);
  }

  public void add (Attribute attribute)
  {
    unappliedAttributes.add (attribute);
  }

  private void applyAttributes (ScreenPosition sp)
  {
    for (Attribute attribute : unappliedAttributes)
      sp.addAttribute (attribute);
    unappliedAttributes.clear ();
  }

  // ---------------------------------------------------------------------------------//
  // Cursor movement
  // ---------------------------------------------------------------------------------//

  public void tab (boolean backTab)
  {
    if (currentField == null)
      return;

    Field newField = null;

    if (currentField.isUnprotected ())
    {
      int first = currentField.getFirstLocation ();
      int sfaPosition = screen.validate (first - 1);

      if (backTab)
      {
        if (currentPosition == first || currentPosition == sfaPosition)
          newField = currentField.getPreviousUnprotectedField ();
        else
          newField = currentField;
      }
      else
      {
        if (currentPosition == sfaPosition)
          newField = currentField;
        else
          newField = currentField.getNextUnprotectedField ();
      }
    }
    else
    {
      if (backTab)
        newField = currentField.getPreviousUnprotectedField ();
      else
        newField = currentField.getNextUnprotectedField ();
    }

    moveTo (newField.getFirstLocation ());
  }

  public void newLine ()
  {
    if (currentField == null)
      return;

    int oldRow = currentPosition / screen.columns;
    int pos = currentPosition;

    while (true)
    {
      tab (false);
      if (currentPosition <= pos)     // backwards or didn't move
        break;
      int newRow = currentPosition / screen.columns;
      if (newRow != oldRow)
        break;
    }
  }

  public void move (Direction direction)
  {
    switch (direction)
    {
      case RIGHT:
        moveTo (currentPosition + 1);
        break;

      case LEFT:
        moveTo (currentPosition - 1);
        break;

      case UP:
        moveTo (currentPosition - screen.columns);
        break;

      case DOWN:
        moveTo (currentPosition + screen.columns);
        break;
    }
  }

  public void moveTo (int newPosition)
  {
    int oldPosition = currentPosition;
    currentPosition = screen.validate (newPosition);

    if (currentPosition != oldPosition)
    {
      notifyCursorMove (oldPosition, currentPosition, currentField, 0);

      if (visible)
      {
        screen.drawPosition (oldPosition, WITHOUT_CURSOR);
        screen.drawPosition (currentPosition, WITH_CURSOR);
      }

      if (currentField != null && !currentField.contains (currentPosition))
        setCurrentField ();
    }
  }

  // ---------------------------------------------------------------------------------//
  // Update currentField
  // ---------------------------------------------------------------------------------//

  private void resetCurrentField ()
  {
    Field lastField = currentField;
    currentField = null;
    if (currentField != lastField)
      notifyFieldChange (lastField, currentField, 0);
  }

  private void setCurrentField ()
  {
    Field lastField = currentField;
    currentField = screen.getField (currentPosition);
    if (currentField != lastField)
      notifyFieldChange (lastField, currentField, 0);
  }

  // ---------------------------------------------------------------------------------//
  // Listener events
  // ---------------------------------------------------------------------------------//

  private final Set<FieldChangeListener> fieldChangeListeners = new HashSet<> ();
  private final Set<CursorMoveListener> cursorMoveListeners = new HashSet<> ();

  void notifyFieldChange (Field oldField, Field currentField, int offset)
  {
    for (FieldChangeListener listener : fieldChangeListeners)
      listener.fieldChanged (oldField, currentField);
  }

  public void addFieldChangeListener (FieldChangeListener listener)
  {
    fieldChangeListeners.add (listener);
  }

  public void removeFieldChangeListener (FieldChangeListener listener)
  {
    fieldChangeListeners.remove (listener);
  }

  void notifyCursorMove (int oldLocation, int currentLocation, Field currentField,
      int offset)
  {
    for (CursorMoveListener listener : cursorMoveListeners)
      listener.cursorMoved (oldLocation, currentLocation, currentField);
  }

  public void addCursorMoveListener (CursorMoveListener listener)
  {
    cursorMoveListeners.add (listener);
  }

  public void removeCursorMoveListener (CursorMoveListener listener)
  {
    cursorMoveListeners.remove (listener);
  }
}