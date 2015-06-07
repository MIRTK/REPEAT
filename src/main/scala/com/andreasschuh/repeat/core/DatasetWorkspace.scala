package com.andreasschuh.repeat.core


/**
 * Get new workspace info object for a specific dataset
 */
object DatasetWorkspace {
  def apply(dataSet: Dataset) = new DatasetWorkspace(dataSet)
}


/**
 * Workspace for a specific dataset
 */
class DatasetWorkspace(dataSet: Dataset) extends Workspace {

  /** Template image used for spatial normalization */
  def refImg(refId: String) =
    if (dataSet.shared) dataSet.refPath(refId = refId)
    else Workspace.refImg(setId = dataSet.id, refId = refId)

  /** Background intensity, padding value */
  def padVal = dataSet.padVal

  /** Get file path of log files directory */
  def logDir = Workspace.logDir(setId = dataSet.id)

  /** Get file path of image meta-data table */
  def imgCsv = {
    if (dataSet.shared) dataSet.imgCsv
    else Workspace.imgCsv(setId = dataSet.id)
  }

  /** Get file path of segmentation labels table */
  def segCsv = {
    if (dataSet.shared) dataSet.segCsv
    else Workspace.segCsv(setId = dataSet.id)
  }

  /** Get file path of original intensity image */
  def orgImg(imgId: String) = {
    if (dataSet.shared) dataSet.imgPath(imgId = imgId)
    else Workspace.orgImg(setId = dataSet.id, imgId = imgId)
  }

  /** Get file path of original image mask */
  def orgMsk(imgId: String) = {
    if (dataSet.shared) dataSet.mskPath(imgId = imgId)
    else Workspace.orgMsk(setId = dataSet.id, imgId = imgId)
  }

  /** Get file path of original segmentation image */
  def orgSeg(imgId: String) = {
    if (dataSet.shared) dataSet.segPath(imgId = imgId)
    else Workspace.orgSeg(setId = dataSet.id, imgId = imgId)
  }

  /** Get file path of padded intensity image */
  def padImg(imgId: String) = Workspace.padImg(setId = dataSet.id, imgId = imgId)

  /** Get file path of image voxel-center points file */
  def imgPts(imgId: String) = Workspace.imgPts(setId = dataSet.id, imgId = imgId)

  /** Get file path of rigid template to image transformation */
  def rigDof(imgId: String, refId: String) = Workspace.rigDof(setId = dataSet.id, refId = refId, imgId = imgId)

  /** Get file path of affine template to image transformation */
  def affDof(imgId: String, refId: String) = Workspace.affDof(setId = dataSet.id, refId = refId, imgId = imgId)

}
