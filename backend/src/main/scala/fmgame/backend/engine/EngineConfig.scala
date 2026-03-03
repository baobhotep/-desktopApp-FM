package fmgame.backend.engine

import fmgame.backend.domain.*
import fmgame.shared.domain.*
import zio.*

/**
 * Konfiguracja modeli silnika: wybór implementacji xG i VAEP (formula | loadable | onnx) oraz ścieżki do plików.
 * Odczyt z zmiennych środowiskowych: ENGINE_XG_MODEL, ENGINE_XG_PATH, ENGINE_VAEP_MODEL, ENGINE_VAEP_PATH.
 */
case class EngineConfig(
  xgModelType: String = "formula",
  xgModelPath: Option[String] = None,
  vaepModelType: String = "formula",
  vaepModelPath: Option[String] = None
)

object EngineConfig {
  /** Odczyt konfiguracji ze zmiennych środowiskowych (w efekcie dla testowalności). */
  def fromEnvZIO: ZIO[Any, Nothing, EngineConfig] = ZIO.succeed(fromEnv)

  def fromEnv: EngineConfig = EngineConfig(
    xgModelType = Option(java.lang.System.getenv("ENGINE_XG_MODEL")).filter(_.nonEmpty).getOrElse("formula"),
    xgModelPath = Option(java.lang.System.getenv("ENGINE_XG_PATH")).filter(_.nonEmpty),
    vaepModelType = Option(java.lang.System.getenv("ENGINE_VAEP_MODEL")).filter(_.nonEmpty).getOrElse("formula"),
    vaepModelPath = Option(java.lang.System.getenv("ENGINE_VAEP_PATH")).filter(_.nonEmpty)
  )
}

/** Aktualnie używane implementacje modeli (można podmieniać w runtime przez endpoint wgrywania). */
case class EngineModels(xg: xGModel, vaep: VAEPModel)

/**
 * Fabryka modeli z konfiguracji. formula = FormulaBased*, loadable = LoadablexGModel.fromJson (path = plik JSON),
 * onnx = OnnxXGModel (wymaga zależności onnxruntime; przy błędzie fallback do formula).
 */
object EngineModelFactory {
  def fromConfig(config: EngineConfig): EngineModels = EngineModels(
    xg = xGModelFromConfig(config.xgModelType, config.xgModelPath),
    vaep = vaepModelFromConfig(config.vaepModelType, config.vaepModelPath)
  )

  /** Wersja w ZIO: I/O plików w efekcie, błędy jako wartość. */
  def fromConfigZIO(config: EngineConfig): ZIO[Any, Throwable, EngineModels] =
    for {
      xg <- xGModelFromConfigZIO(config.xgModelType, config.xgModelPath)
      vaep <- vaepModelFromConfigZIO(config.vaepModelType, config.vaepModelPath)
    } yield EngineModels(xg, vaep)

  def xGModelFromConfig(modelType: String, pathOpt: Option[String]): xGModel =
    (modelType.toLowerCase, pathOpt) match {
      case ("loadable", Some(path)) =>
        scala.util.Try(scala.io.Source.fromFile(path).mkString).toOption
          .fold[xGModel](FormulaBasedxG)(LoadablexGModel.fromJson)
      case ("onnx", Some(path)) =>
        OnnxXGModel.load(path).getOrElse(FormulaBasedxG)
      case _ => FormulaBasedxG
    }

  def xGModelFromConfigZIO(modelType: String, pathOpt: Option[String]): ZIO[Any, Throwable, xGModel] =
    (modelType.toLowerCase, pathOpt) match {
      case ("loadable", Some(path)) =>
        ZIO.attempt {
          val s = scala.io.Source.fromFile(path)
          try s.mkString finally s.close()
        }.map(LoadablexGModel.fromJson).catchAll(_ => ZIO.succeed(FormulaBasedxG))
      case ("onnx", Some(path)) =>
        ZIO.attempt(OnnxXGModel.load(path).getOrElse(FormulaBasedxG)).catchAll(_ => ZIO.succeed(FormulaBasedxG))
      case _ => ZIO.succeed(FormulaBasedxG)
    }

  def vaepModelFromConfig(modelType: String, pathOpt: Option[String]): VAEPModel =
    (modelType.toLowerCase, pathOpt) match {
      case ("onnx", Some(path)) =>
        OnnxVAEPModel.load(path).getOrElse(FormulaBasedVAEP)
      case _ => FormulaBasedVAEP
    }

  def vaepModelFromConfigZIO(modelType: String, pathOpt: Option[String]): ZIO[Any, Throwable, VAEPModel] =
    (modelType.toLowerCase, pathOpt) match {
      case ("onnx", Some(path)) =>
        ZIO.attempt(OnnxVAEPModel.load(path).getOrElse(FormulaBasedVAEP)).catchAll(_ => ZIO.succeed(FormulaBasedVAEP))
      case _ => ZIO.succeed(FormulaBasedVAEP)
    }

  /** Buduje xG z zawartości JSON (np. po wgraniu pliku). */
  def xGFromJson(json: String): xGModel = LoadablexGModel.fromJson(json)

  /** Buduje xG z pliku (ścieżka do .json lub .onnx). */
  def xGFromPath(path: String): xGModel =
    if (path.endsWith(".onnx")) OnnxXGModel.load(path).getOrElse(FormulaBasedxG)
    else scala.util.Try(scala.io.Source.fromFile(path).mkString).toOption.fold[xGModel](FormulaBasedxG)(LoadablexGModel.fromJson)
}
