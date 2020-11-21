/*
 * Copyright 2006-2020 The MZmine Development Team
 * 
 * This file is part of MZmine.
 * 
 * MZmine is free software; you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation; either version 2 of the
 * License, or (at your option) any later version.
 * 
 * MZmine is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with MZmine; if not,
 * write to the Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301
 * USA
 */

package io.github.mzmine.modules.dataprocessing.norm_linear;

import io.github.mzmine.datamodel.data.Feature;
import io.github.mzmine.datamodel.data.FeatureList;
import io.github.mzmine.datamodel.data.FeatureList.FeatureListAppliedMethod;
import io.github.mzmine.datamodel.data.FeatureListRow;
import io.github.mzmine.datamodel.data.ModularFeature;
import io.github.mzmine.datamodel.data.ModularFeatureList;
import io.github.mzmine.datamodel.data.ModularFeatureListRow;
import io.github.mzmine.datamodel.data.SimpleFeatureListAppliedMethod;
import io.github.mzmine.util.FeatureUtils;
import java.util.Hashtable;
import java.util.logging.Logger;

import io.github.mzmine.datamodel.MZmineProject;
import io.github.mzmine.datamodel.RawDataFile;
import io.github.mzmine.datamodel.Scan;
import io.github.mzmine.parameters.ParameterSet;
import io.github.mzmine.taskcontrol.AbstractTask;
import io.github.mzmine.taskcontrol.TaskStatus;
import io.github.mzmine.util.FeatureMeasurementType;

class LinearNormalizerTask extends AbstractTask {

  private Logger logger = Logger.getLogger(this.getClass().getName());

  static final float maximumOverallFeatureHeightAfterNormalization = 100000.0f;

  private final MZmineProject project;
  private FeatureList originalFeatureList, normalizedFeatureList;

  private int processedDataFiles, totalDataFiles;

  private String suffix;
  private NormalizationType normalizationType;
  private FeatureMeasurementType featureMeasurementType;
  private boolean removeOriginal;
  private ParameterSet parameters;

  public LinearNormalizerTask(MZmineProject project, FeatureList featureList, ParameterSet parameters) {

    this.project = project;
    this.originalFeatureList = featureList;
    this.parameters = parameters;

    totalDataFiles = originalFeatureList.getNumberOfRawDataFiles();

    suffix = parameters.getParameter(LinearNormalizerParameters.suffix).getValue();
    normalizationType =
        parameters.getParameter(LinearNormalizerParameters.normalizationType).getValue();
    featureMeasurementType =
        parameters.getParameter(LinearNormalizerParameters.featureMeasurementType).getValue();
    removeOriginal = parameters.getParameter(LinearNormalizerParameters.autoRemove).getValue();

  }

  public double getFinishedPercentage() {
    return (double) processedDataFiles / (double) totalDataFiles;
  }

  public String getTaskDescription() {
    return "Linear normalization of " + originalFeatureList + " by " + normalizationType;
  }

  public void run() {

    setStatus(TaskStatus.PROCESSING);
    logger.info("Running linear normalizer");

    // This hashtable maps rows from original alignment result to rows of
    // the normalized alignment
    Hashtable<FeatureListRow, ModularFeatureListRow> rowMap = new Hashtable<>();

    // Create new feature list
    normalizedFeatureList =
        new ModularFeatureList(originalFeatureList + " " + suffix, originalFeatureList.getRawDataFiles());

    // Loop through all raw data files, and find the feature with biggest
    // height
    float maxOriginalHeight = 0f;
    for (RawDataFile file : originalFeatureList.getRawDataFiles()) {
      for (FeatureListRow originalFeatureListRow : originalFeatureList.getRows()) {
        Feature p = originalFeatureListRow.getFeature(file);
        if (p != null) {
          if (maxOriginalHeight <= p.getHeight())
            maxOriginalHeight = p.getHeight();
        }
      }
    }

    // Loop through all raw data files, and normalize feature values
    for (RawDataFile file : originalFeatureList.getRawDataFiles()) {

      // Cancel?
      if (isCanceled()) {
        return;
      }

      // Determine normalization type and calculate normalization factor
      float normalizationFactor = 1.0f;

      // - normalization by average feature intensity
      if (normalizationType == NormalizationType.AverageIntensity) {
        float intensitySum = 0f;
        int intensityCount = 0;
        for (FeatureListRow featureListRow : originalFeatureList.getRows()) {
          Feature p = featureListRow.getFeature(file);
          if (p != null) {
            if (featureMeasurementType == FeatureMeasurementType.HEIGHT) {
              intensitySum += p.getHeight();
            } else {
              intensitySum += p.getArea();
            }
            intensityCount++;
          }
        }
        normalizationFactor = intensitySum / (float) intensityCount;
      }

      // - normalization by average squared feature intensity
      if (normalizationType == NormalizationType.AverageSquaredIntensity) {
        float intensitySum = 0f;
        int intensityCount = 0;
        for (FeatureListRow featureListRow : originalFeatureList.getRows()) {
          Feature p = featureListRow.getFeature(file);
          if (p != null) {
            if (featureMeasurementType == FeatureMeasurementType.HEIGHT) {
              intensitySum += (p.getHeight() * p.getHeight());
            } else {
              intensitySum += (p.getArea() * p.getArea());
            }
            intensityCount++;
          }
        }
        normalizationFactor = intensitySum / (float) intensityCount;
      }

      // - normalization by maximum feature intensity
      if (normalizationType == NormalizationType.MaximumFeatureHeight) {
        float maximumIntensity = 0;
        for (FeatureListRow featureListRow : originalFeatureList.getRows()) {
          Feature p = featureListRow.getFeature(file);
          if (p != null) {
            if (featureMeasurementType == FeatureMeasurementType.HEIGHT) {
              if (maximumIntensity < p.getHeight())
                maximumIntensity = p.getHeight();
            } else {
              if (maximumIntensity < p.getArea())
                maximumIntensity = p.getArea();
            }

          }
        }
        normalizationFactor = maximumIntensity;
      }

      // - normalization by total raw signal
      if (normalizationType == NormalizationType.TotalRawSignal) {
        normalizationFactor = 0;
        for (int scanNumber : file.getScanNumbers(1)) {
          Scan scan = file.getScan(scanNumber);
          normalizationFactor += scan.getTIC();
        }
      }

      // Readjust normalization factor so that maximum height will be
      // equal to maximumOverallFeatureHeightAfterNormalization after
      // normalization
      float maxNormalizedHeight = maxOriginalHeight / normalizationFactor;
      normalizationFactor =
          normalizationFactor * maxNormalizedHeight / maximumOverallFeatureHeightAfterNormalization;

      // Normalize all peak intenisities using the normalization factor
      for (FeatureListRow originalFeatureListRow : originalFeatureList.getRows()) {

        // Cancel?
        if (isCanceled()) {
          return;
        }

        Feature originalFeature = originalFeatureListRow.getFeature(file);
        if (originalFeature != null) {

          ModularFeature normalizedFeature = new ModularFeature(originalFeature);
          FeatureUtils.copyFeatureProperties(originalFeature, normalizedFeature);

          float normalizedHeight = originalFeature.getHeight() / normalizationFactor;
          float normalizedArea = originalFeature.getArea() / normalizationFactor;
          normalizedFeature.setHeight(normalizedHeight);
          normalizedFeature.setArea(normalizedArea);

          ModularFeatureListRow normalizedRow = rowMap.get(originalFeatureListRow);

          if (normalizedRow == null) {

            normalizedRow = new ModularFeatureListRow(
                (ModularFeatureList) originalFeatureListRow.getFeatureList(), originalFeatureListRow.getID());

            FeatureUtils.copyFeatureListRowProperties(originalFeatureListRow, normalizedRow);

            rowMap.put(originalFeatureListRow, normalizedRow);
          }

          normalizedRow.addFeature(file, normalizedFeature);

        }

      }

      // Progress
      processedDataFiles++;

    }

    // Finally add all normalized rows to normalized alignment result
    for (FeatureListRow originalFeatureListRow : originalFeatureList.getRows()) {
      ModularFeatureListRow normalizedRow = rowMap.get(originalFeatureListRow);
      if (normalizedRow == null)
        continue;
      normalizedFeatureList.addRow(normalizedRow);
    }

    // Add new feature list to the project
    project.addFeatureList(normalizedFeatureList);

    // Load previous applied methods
    for (FeatureListAppliedMethod proc : originalFeatureList.getAppliedMethods()) {
      normalizedFeatureList.addDescriptionOfAppliedTask(proc);
    }

    // Add task description to feature List
    normalizedFeatureList.addDescriptionOfAppliedTask(new SimpleFeatureListAppliedMethod(
        "Linear normalization of by " + normalizationType, parameters));

    // Remove the original feature list if requested
    if (removeOriginal)
      project.removePeakList(originalFeatureList);

    logger.info("Finished linear normalizer");
    setStatus(TaskStatus.FINISHED);

  }

}
