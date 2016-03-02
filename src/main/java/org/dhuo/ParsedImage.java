package org.dhuo;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Holds the parsed properties plus BufferedImage from a DICOM image.
 */
public class ParsedImage {
  public BufferedImage image;

  // Series number + slice location form a unique slice key (some series have multiple locations).
  public int seriesNumber;
  public double sliceLocation;

  // To avoid relying on image name, we can use triggerTime to sort the frames of a slice series.
  public double triggerTime;

  public double sliceThickness;
  public double pixelSpacingX;
  public double pixelSpacingY;

  public byte[] toBytes() throws IOException {
    ByteArrayOutputStream boutBuf = new ByteArrayOutputStream();
    DataOutputStream bout = new DataOutputStream(boutBuf);
    ImageIO.write(image, "png", bout);
    bout.writeInt(seriesNumber);
    bout.writeDouble(sliceLocation);
    bout.writeDouble(triggerTime);
    bout.writeDouble(sliceThickness);
    bout.writeDouble(pixelSpacingX);
    bout.writeDouble(pixelSpacingY);
    bout.flush();
    bout.close();
    return boutBuf.toByteArray();
  }

  public static ParsedImage fromBytes(byte[] buf) throws IOException {
    ParsedImage ret = new ParsedImage();
    DataInputStream bin = new DataInputStream(new ByteArrayInputStream(buf));
    ret.image = ImageIO.read(bin);
    ret.seriesNumber = bin.readInt();
    ret.sliceLocation = bin.readDouble();
    ret.triggerTime = bin.readDouble();
    ret.sliceThickness = bin.readDouble();
    ret.pixelSpacingX = bin.readDouble();
    ret.pixelSpacingY = bin.readDouble();
    return ret;
  }
}
