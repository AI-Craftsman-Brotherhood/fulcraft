package com.craftsmanbro.fulcraft.ui.tui;

import java.util.Arrays;

/** Mutable text buffer for TUI input editing without exposing StringBuilder fields. */
public final class MutableTextBuffer {

  private char[] buffer;

  private int length;

  public MutableTextBuffer() {
    this(64);
  }

  public MutableTextBuffer(final int initialCapacity) {
    final int effectiveCapacity = Math.max(1, initialCapacity);
    this.buffer = new char[effectiveCapacity];
    this.length = 0;
  }

  public void append(final char c) {
    ensureCapacity(length + 1);
    buffer[length++] = c;
  }

  public void append(final String text) {
    // Preserve StringBuilder-style null handling for appended text.
    final String textToAppend = text == null ? "null" : text;
    if (textToAppend.isEmpty()) {
      return;
    }
    final int textLength = textToAppend.length();
    ensureCapacity(length + textLength);
    textToAppend.getChars(0, textLength, buffer, length);
    length += textLength;
  }

  public int length() {
    return length;
  }

  public boolean isEmpty() {
    return length == 0;
  }

  public void deleteCharAt(final int index) {
    if (index < 0 || index >= length) {
      throw new IndexOutOfBoundsException("index: " + index + ", length: " + length);
    }
    if (index < length - 1) {
      System.arraycopy(buffer, index + 1, buffer, index, length - index - 1);
    }
    length--;
  }

  public void setLength(final int newLength) {
    if (newLength < 0 || newLength > length) {
      throw new IndexOutOfBoundsException("newLength: " + newLength + ", length: " + length);
    }
    length = newLength;
  }

  @Override
  public String toString() {
    return new String(buffer, 0, length);
  }

  private void ensureCapacity(final int minimumCapacity) {
    if (minimumCapacity <= buffer.length) {
      return;
    }

    final int expandedCapacity = Math.max(buffer.length * 2, minimumCapacity);
    buffer = Arrays.copyOf(buffer, expandedCapacity);
  }
}
