/*
 * Copyright 2006-2022 The MZmine Development Team
 *
 * This file is part of MZmine.
 *
 * MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 *
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with MZmine; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 */
/*
 * This module was prepared by Abi Sarvepalli, Christopher Jensen, and Zheng Zhang at the Dorrestein
 * Lab (University of California, San Diego).
 *
 * It is freely available under the GNU GPL licence of MZmine2.
 *
 * For any questions or concerns, please refer to:
 * https://groups.google.com/forum/#!forum/molecular_networking_bug_reports
 *
 * Credit to the Du-Lab development team for the initial commitment to the MGF export module.
 */

package io.github.mzmine.modules.io.spectraldbsubmit.formats;

import io.github.mzmine.datamodel.DataPoint;
import io.github.mzmine.datamodel.PolarityType;
import io.github.mzmine.modules.io.spectraldbsubmit.formats.GnpsValues.Polarity;
import io.github.mzmine.modules.io.spectraldbsubmit.param.LibraryMetaDataParameters;
import io.github.mzmine.modules.io.spectraldbsubmit.param.LibrarySubmitIonParameters;
import io.github.mzmine.util.spectraldb.entry.DBEntryField;
import io.github.mzmine.util.spectraldb.entry.SpectralDBEntry;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.List;

public class MSPEntryGenerator {

  public static final List<DBEntryField> EXPORT_FIELDS = List.of(DBEntryField.NAME, DBEntryField.MZ,
      DBEntryField.RT, DBEntryField.CCS, DBEntryField.ION_TYPE, DBEntryField.EXACT_MASS,
      DBEntryField.MS_LEVEL, DBEntryField.CHARGE, DBEntryField.FORMULA, DBEntryField.SMILES,
      DBEntryField.INCHI, DBEntryField.INCHIKEY, DBEntryField.FRAGMENTATION_METHOD,
      DBEntryField.COLLISION_ENERGY, DBEntryField.ISOLATION_WINDOW,
      DBEntryField.MSN_FRAGMENTATION_METHODS, DBEntryField.MSN_COLLISION_ENERGIES,
      DBEntryField.MSN_PRECURSOR_MZS, DBEntryField.MSN_ISOLATION_WINDOWS);

  /**
   * Creates a simple MSP nist format DB entry
   *
   * @param param
   * @param dps
   * @return
   */
  public static String createMSPEntry(LibrarySubmitIonParameters param, DataPoint[] dps) {

    LibraryMetaDataParameters meta = (LibraryMetaDataParameters) param.getParameter(
        LibrarySubmitIonParameters.META_PARAM).getValue();

    boolean exportRT = meta.getParameter(LibraryMetaDataParameters.EXPORT_RT).getValue();
    String ionMode =
        meta.getParameter(LibraryMetaDataParameters.IONMODE).getValue().equals(Polarity.Positive)
            ? "P" : "N";

    String def = ": ";
    String br = "\n";
    StringBuilder s = new StringBuilder();
    // tag spectrum from mzmine
    // ion specific
    s.append(DBEntryField.NAME.getNistMspID() + def + meta.getParameter(
        LibraryMetaDataParameters.COMPOUND_NAME).getValue() + br);
    s.append(DBEntryField.INCHIKEY.getNistMspID() + def + meta.getParameter(
        LibraryMetaDataParameters.INCHI_AUX).getValue() + br);
    s.append(DBEntryField.MS_LEVEL.getNistMspID() + def + "MS" + meta.getParameter(
        LibraryMetaDataParameters.MS_LEVEL).getValue() + br);
    s.append(DBEntryField.INSTRUMENT_TYPE.getNistMspID() + def + meta.getParameter(
        LibraryMetaDataParameters.INSTRUMENT).getValue() + br);
    s.append(DBEntryField.INSTRUMENT.getNistMspID() + def + meta.getParameter(
        LibraryMetaDataParameters.INSTRUMENT_NAME).getValue() + br);
    s.append(DBEntryField.ION_MODE.getNistMspID() + def + ionMode + br);
    s.append(DBEntryField.COLLISION_ENERGY.getNistMspID() + def + meta.getParameter(
        LibraryMetaDataParameters.FRAGMENTATION_METHOD).getValue() + br);
    s.append(DBEntryField.FORMULA.getNistMspID() + def + meta.getParameter(
        LibraryMetaDataParameters.FORMULA).getValue() + br);

    Double exact = meta.getParameter(LibraryMetaDataParameters.EXACT_MASS).getValue();
    if (exact != null) {
      s.append(DBEntryField.EXACT_MASS.getNistMspID() + def + exact + br);
    }

    Double precursorMZ = param.getParameter(LibrarySubmitIonParameters.MZ).getValue();
    if (precursorMZ != null) {
      s.append(
          DBEntryField.MZ.getNistMspID() + def + param.getParameter(LibrarySubmitIonParameters.MZ)
              .getValue() + br);
    }

    String adduct = param.getParameter(LibrarySubmitIonParameters.ADDUCT).getValue();
    if (adduct != null && !adduct.trim().isEmpty()) {
      s.append(DBEntryField.ION_TYPE.getNistMspID() + def + param.getParameter(
          LibrarySubmitIonParameters.ADDUCT).getValue() + br);
    }

    if (exportRT) {
      Double rt = meta.getParameter(LibraryMetaDataParameters.EXPORT_RT).getEmbeddedParameter()
          .getValue();
      if (rt != null) {
        s.append(DBEntryField.RT.getNistMspID() + def + rt + br);
      }
    }

    // num peaks and data
    s.append(DBEntryField.NUM_PEAKS.getNistMspID() + def + dps.length + br);

    NumberFormat mzForm = new DecimalFormat("0.######");
    for (DataPoint dp : dps) {
      s.append(mzForm.format(dp.getMZ()) + " " + dp.getIntensity() + br);
    }
    s.append(br);
    return s.toString();
  }

  /**
   * Creates a simple MSP nist format DB entry
   */
  public static String createMSPEntry(SpectralDBEntry entry) {

    String def = ": ";
    String br = "\n";
    StringBuilder s = new StringBuilder();
    // tag spectrum from mzmine
    // export sorted fields first, then the rest
    for (DBEntryField field : EXPORT_FIELDS) {
      String id = field.getNistMspID();
      if (id == null || id.isBlank()) {
        continue;
      }
      entry.getField(field)
          .ifPresent(value -> s.append(field.getNistMspID()).append(def).append(value).append(br));
    }
    entry.getField(DBEntryField.ION_MODE).ifPresent(p -> {
      String pol = PolarityType.POSITIVE.equals(p) ? "P" : "N";
      s.append(DBEntryField.ION_MODE.getNistMspID()).append(def).append(pol).append(br);
    });

    // num peaks and data
    DataPoint[] dps = entry.getDataPoints();
    s.append(DBEntryField.NUM_PEAKS.getNistMspID()).append(def).append(dps.length).append(br);

    NumberFormat mzForm = new DecimalFormat("0.######");
    NumberFormat percentForm = new DecimalFormat("0.###");

    double max = Arrays.stream(dps).mapToDouble(DataPoint::getIntensity).max().orElse(1d);
    for (DataPoint dp : dps) {
      s.append(mzForm.format(dp.getMZ())).append(" ")
          .append(percentForm.format(dp.getIntensity() / max * 100.0)).append(br);
    }
    s.append(br);
    return s.toString();
  }
}
