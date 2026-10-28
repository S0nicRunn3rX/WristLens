package com.daniil.wristlens.core;
import org.junit.Test;import static org.junit.Assert.*;
public class PreviewProfileTest {
 @Test public void presetsHaveMeaningfullyDifferentRateAndCompression(){PreviewProfile eco=PreviewProfile.of("eco"),smooth=PreviewProfile.of("auto"),detail=PreviewProfile.of("detail");assertEquals(320,eco.preferredWidth());assertEquals(0,smooth.preferredWidth());assertEquals(0,detail.preferredWidth());assertTrue(eco.jpegQuality()<smooth.jpegQuality());assertTrue(smooth.jpegQuality()<detail.jpegQuality());assertTrue(smooth.intervalMs()<detail.intervalMs());assertTrue(detail.intervalMs()<eco.intervalMs());}
}
