package com.andreasschuh.repeat.core


/**
 * Get new workspace info object for a specific dataset and registration
 */
object EvaluationWorkspace {
  def apply(dataSet: Dataset, reg: Registration) = new EvaluationWorkspace(dataSet, reg)
}


/**
 * Workspace for a specific dataset and registration
 */
class EvaluationWorkspace(dataSet: Dataset, reg: Registration) extends DatasetWorkspace(dataSet) {

  /** Get file path of registration log files directory */
  def logDir(parId: String) = Workspace.logDir(setId = dataSet.id).resolve(reg.id + "-" + parId)

  /** Get file path of registration parameters table */
  def parCsv = Workspace.parCsv(setId = dataSet.id, regId = reg.id)

  /** Get file path of registered output image */
  def regImg(parId: String, imgId: String) = Workspace.regImg(setId = dataSet.id, regId = reg.id, parId = parId, imgId = imgId)

  /** Get file path of registered segmentation image */
  def regSeg(parId: String, imgId: String) = Workspace.regSeg(setId = dataSet.id, regId = reg.id, parId = parId, imgId = imgId)

  /** Get file path of intensity average image */
  def avgImg(parId: String, refId: String) = Workspace.avgImg(setId = dataSet.id, regId = reg.id, parId = parId, refId = refId)

}
