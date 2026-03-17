package fmgame.desktop

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.{Pixmap, Texture}
import com.badlogic.gdx.scenes.scene2d.ui._
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.Disposable

/** Skórka UI z czcionką obsługującą polskie znaki (FreeType) i stylami dla czytelności. */
object Assets extends Disposable {

  private val (font, titleFont) = makeFonts()
  private val skin = new Skin()

  skin.add("default-font", font)
  skin.add("title-font", titleFont)

  val labelStyle = new Label.LabelStyle(font, Color.WHITE)
  skin.add("default", labelStyle)
  val titleStyle = new Label.LabelStyle(titleFont, new Color(1f, 0.9f, 0.5f, 1f))
  skin.add("title", titleStyle)

  val btnStyle = new TextButton.TextButtonStyle()
  btnStyle.font = font
  btnStyle.fontColor = Color.WHITE
  btnStyle.up = newDrawable(0.25f, 0.28f, 0.35f, 1f)
  btnStyle.down = newDrawable(0.2f, 0.22f, 0.3f, 1f)
  skin.add("default", btnStyle)

  val tfStyle = new TextField.TextFieldStyle()
  tfStyle.font = font
  tfStyle.fontColor = Color.WHITE
  tfStyle.background = newDrawable(0.12f, 0.12f, 0.15f, 1f)
  skin.add("default", tfStyle)

  private def newDrawable(r: Float, g: Float, b: Float, a: Float): Drawable = {
    val pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888)
    pixmap.setColor(r, g, b, a)
    pixmap.fill()
    val drawable = new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(
      new com.badlogic.gdx.graphics.g2d.TextureRegion(new Texture(pixmap))
    )
    pixmap.dispose()
    drawable
  }

  private def whiteDrawable(size: Int): Drawable = {
    val pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888)
    pixmap.setColor(Color.WHITE)
    pixmap.fill()
    val drawable = new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(
      new com.badlogic.gdx.graphics.g2d.TextureRegion(new Texture(pixmap))
    )
    pixmap.dispose()
    drawable
  }

  val checkBoxStyle = new CheckBox.CheckBoxStyle(whiteDrawable(24), whiteDrawable(24), font, Color.WHITE)
  skin.add("default", checkBoxStyle)

  val scrollStyle = new ScrollPane.ScrollPaneStyle()
  scrollStyle.background = sectionBackground
  skin.add("default", scrollStyle)

  val sliderStyle = new Slider.SliderStyle()
  sliderStyle.background = newDrawable(0.2f, 0.2f, 0.28f, 1f)
  sliderStyle.knob = whiteDrawable(16)
  sliderStyle.knobBefore = newDrawable(0.35f, 0.45f, 0.6f, 1f)
  skin.add("default", sliderStyle)

  /** Pasek postępu 0–20 (atrybuty: Fiz/Tech/Men/Obr). */
  val progressBarStyle = new ProgressBar.ProgressBarStyle()
  progressBarStyle.background = newDrawable(0.2f, 0.2f, 0.28f, 1f)
  progressBarStyle.knobBefore = newDrawable(0.3f, 0.5f, 0.7f, 1f)
  skin.add("default", progressBarStyle)

  val selectBoxStyle = new SelectBox.SelectBoxStyle()
  selectBoxStyle.font = font
  selectBoxStyle.fontColor = Color.WHITE
  selectBoxStyle.background = newDrawable(0.18f, 0.18f, 0.24f, 1f)
  selectBoxStyle.scrollStyle = scrollStyle
  selectBoxStyle.listStyle = new List.ListStyle()
  selectBoxStyle.listStyle.font = font
  selectBoxStyle.listStyle.fontColorSelected = Color.WHITE
  selectBoxStyle.listStyle.fontColorUnselected = Color.WHITE
  selectBoxStyle.listStyle.selection = newDrawable(0.3f, 0.4f, 0.55f, 1f)
  selectBoxStyle.listStyle.background = newDrawable(0.14f, 0.14f, 0.2f, 1f)
  skin.add("default", selectBoxStyle)

  val windowStyle = new Window.WindowStyle(font, Color.WHITE, newDrawable(0.14f, 0.14f, 0.2f, 0.98f))
  skin.add("default", windowStyle)

  def getSkin: Skin = skin
  def getFont: BitmapFont = font
  def getTitleFont: BitmapFont = titleFont

  /** Tło ekranu – ciemny szary. */
  val screenBackgroundColor = new Color(0.12f, 0.12f, 0.16f, 1f)
  /** Drawable na tło sekcji (np. tabela, terminarz). */
  def sectionBackground: Drawable = newDrawable(0.16f, 0.16f, 0.22f, 0.95f)
  /** Spójne odstępy UX: między sekcjami. */
  val padSection: Float = 16f
  /** Spójne odstępy UX: między kontrolkami. */
  val padControl: Float = 8f

  private def makeFonts(): (BitmapFont, BitmapFont) = {
    val polishChars = "ąćęłńóśźżĄĆĘŁŃÓŚŹŻ"
    val charset = com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.DEFAULT_CHARS + polishChars

    def tryLoadFont(size: Int): Option[BitmapFont] = {
      val paths = Seq(
        Gdx.files.internal("fonts/DejaVuSans.ttf"),
        Gdx.files.internal("font.ttf"),
        Gdx.files.absolute("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"),
        Gdx.files.absolute("/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf"),
        Gdx.files.absolute(System.getProperty("user.home") + "/.local/share/fonts/DejaVuSans.ttf"),
      )
      paths.find(_.exists()).flatMap { fh =>
        try {
          val gen = new com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator(fh)
          val param = new com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter()
          param.size = size
          param.characters = charset
          param.color = com.badlogic.gdx.graphics.Color.WHITE
          val f = gen.generateFont(param)
          gen.dispose()
          Some(f)
        } catch { case _: Exception => None }
      }
    }

    tryLoadFont(18) match {
      case Some(f) =>
        f.getData.setScale(1.2f)
        val titleF = tryLoadFont(22).getOrElse {
          val t = new BitmapFont()
          t.getData.setScale(1.6f)
          t
        }
        if (!titleF.equals(f)) titleF.getData.setScale(1.1f)
        (f, titleF)
      case None =>
        val fallback = new BitmapFont()
        fallback.getData.setScale(1.4f)
        val titleF = new BitmapFont()
        titleF.getData.setScale(1.8f)
        if (Gdx.app != null) Gdx.app.log("FMGame", "Brak TTF z polskimi znakami – używana czcionka domyślna (ą,ę,ó mogą być kwadracikami). Dodaj fonts/DejaVuSans.ttf.")
        (fallback, titleF)
    }
  }

  override def dispose(): Unit = {
    skin.dispose()
    font.dispose()
    titleFont.dispose()
  }
}
