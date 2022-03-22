package twotm8
package frontend

import scalacss.ProdDefaults.*
import scalacss.internal.StyleA
import scalacss.internal.Attrs.justifyItems

object Styles extends StyleSheet.Inline:
  import dsl.*

  private val weirdWhiteColor =
    mixin(
      fontFamily :=! "Verdana, Geneva, Tahoma, sans-serif",
      color.white,
      textShadow := "2px 2px 0px black"
    )

  private val W = 1024.px

  val logoHeader = style(
    marginBottom(20.px),
    width(100.%%),
    maxWidth(W),
    display.flex,
    justifyContent.spaceBetween,
    justifyItems.center
  )

  val navButton = style(
    backgroundColor(c"#626ac8"),
    padding(5.px),
    border(0.px),
    color.white,
    fontWeight.bold,
    cursor.pointer
  )

  val inputLabel = style(
    padding(3.px),
    fontSize(1.5.rem)
  )

  val welcomeBanner = style(weirdWhiteColor)

  val thoughtLeaderHeader = style(
    color(c"#1f202c"),
    fontSize(2.rem),
    textDecorationLine.none
  )

  val logo = style(
    fontWeight.bold,
    fontStyle.italic,
    fontSize := 3.rem,
    margin := 0.px,
    weirdWhiteColor
  )

  val createForm = style(
    display.flex,
    width(100.%%),
    height(100.px),
    margin(0.px),
    gap(5.px),
    justifyContent.spaceAround
  )

  val twotInputBlock = style(
    flexGrow(5)
  )

  val rageButtonBlock = style(
  )

  val inputTwot = style(
    width(100.%%),
    height(100.%%),
    padding(10.px),
    fontSize(1.7.rem),
    boxSizing.borderBox
  )

  val inputNickname = inputTwot
  val inputPassword = inputTwot

  val rageButton = style(
    width(100.px),
    height(100.%%),
    fontSize(1.5.rem)
  )

  val infoText =
    style(weirdWhiteColor, fontSize(1.5.rem), fontWeight.bold, fontStyle.italic)

  val infoLink = style(
    weirdWhiteColor,
    fontSize(1.5.rem),
    fontWeight.bold,
    textDecorationLine.underline,
    &.hover(
      textDecorationLine.none
    )
  )

  val errorFlash = style(
    padding(10.px),
    backgroundColor.darkred,
    color.white
  )

  val logoSubtitle = style(
    fontSize(1.rem),
    color.white,
    fontFamily :=! "Verdana, Geneva, Tahoma, sans-serif",
    flexGrow(4),
    textAlign.center
  )

  val twot = style(
    backgroundColor.white,
    padding := 8.px,
    borderRadius(6.px),
    fontSize(1.5.rem),
    width(100.%%)
  )

  val twotCard = style(
    display.flex,
    width(100.%%),
    height(100.%%),
    margin(0.px),
    padding(0.px),
    gap(5.px)
  )

  val twotTitle = style(
    display.flex,
    justifyContent.spaceBetween,
    backgroundColor(c"#dcd79b"),
    padding(8.px),
    borderRadius(6.px, 6.px, 0.px, 0.px),
    margin(-8.px),
    marginBottom(6.px)
  )

  val deleteButton = style(
    border(0.px),
    cursor.pointer
  )

  val twotText = style(
    fontWeight.bold,
    fontSize(1.7.rem),
    overflow.scroll,
    overflowWrap := "anywhere",
    textTransform.uppercase
  )

  val twots = style(
    width(100.%%),
    maxWidth(W),
    gap(15.px),
    display.flex,
    flexDirection.column,
    alignItems.flexStart,
    justifyItems.flexStart
  )

  val twotUwotm8 = style(
    margin(0.px),
    padding(0.px)
  )

  val container = style(
    display.flex,
    flexDirection.column,
    justifyContent.center,
    alignItems.center,
    height(100.%%)
  )

  val subContainer = style(
    width(100.%%),
    maxWidth(W)
  )

  val profileLink = style(
    textDecorationLine.underline,
    color.black,
    &.visited(color.black),
    &.hover(textDecorationLine.none)
  )

  val uwotm8ButtonCommon = mixin(
    width(100.px),
    height(100.%%),
    fontSize(1.2.rem),
    fontWeight.bold,
    cursor.pointer
  )

  private val uwotm8Pressed =
    mixin(
      backgroundColor.darkred,
      border(0.px),
      color.white,
      uwotm8ButtonCommon
    )

  private val uwotm8NotPressed =
    mixin(
      backgroundColor.white,
      border(1.px, solid, darkred),
      color.darkred,
      uwotm8ButtonCommon
    )

  val uwotm8Button = styleF.bool {
    case false =>
      styleS(uwotm8NotPressed)
    case true =>
      styleS(uwotm8Pressed)
  }

  private val followButtonCommon =
    mixin(
      border(1.px, solid),
      fontSize(1.2.rem),
      padding(5.px)
    )

  import FollowState.*
  val followButton = styleF[FollowState](Domain.ofValues(Yes, No, Hide)) {
    case Yes =>
      styleS(
        backgroundColor(rgb(159, 216, 159)),
        borderColor.white,
        followButtonCommon
      )
    case No =>
      styleS(
        backgroundColor.white,
        borderColor(rgb(159, 216, 159)),
        followButtonCommon
      )
    case Hide => styleS(display.none)
  }

  object Standalone extends StyleSheet.Standalone:
    import dsl.*
    "body" - (
      backgroundColor(c"#9ba0dc"),
      fontFamily :=! "Arial, Helvetica, sans-serif"
    )

  Standalone.styles
end Styles

import com.raquo.laminar.api.L.*

given Conversion[StyleA, Setter[HtmlElement]] with
  def apply(st: StyleA): Setter[HtmlElement] = (cls := st.htmlClass)
