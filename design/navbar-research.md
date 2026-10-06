# SLConsole 底栏第二轮：参考研究

这一轮只研究「能安静用很久」的界面，不研究道具。第一轮的战术 HUD、樱花气泡、机甲菱形和全息弧被否，问题不在题材，而在装饰自己在演戏：假发光、假全息、贴上去的纹理。下面这些参考的共同点是，辨识度来自一套构造规则（点、线、游标、字号），材料在静止时是实的。粒子如果出现，也是短时间、有事由的。

现有应用里继续沿用的事实：底栏现在是五项（概览 / 玩家 / 控制台 / 地图 / 中心），可选液态玻璃走 Kyant Backdrop，模糊要 Android 12，折射要 Android 13。新结构是四项加一个分开的快捷动作（广播 / 重启回合 / CASSIE），地图收进概览。品牌色樱粉 `#F6B8D1`、淡紫 `#C9B6F2`、天蓝 `#A9D4F5` 只留给粒子和少量状态，大面积仍是 `#10151C` / `#161B22` 和纸色浅底。

## 参考

1. **Nothing OS 3 的点，以及 Phone 3 的 Glyph Matrix**  
   [Android Authority：Nothing OS 3.0](https://www.androidauthority.com/nothing-os-3-hands-on-3488739/)  
   [Glyph Matrix 怎么工作](https://www.androidauthority.com/nothing-phone-3-glyph-matrix-features-3570323/)  
   借：点是一种材料，只有亮和灭，分辨率故意低，远看也能认。指纹解锁时的涟漪点是「事件触发的点」，不是永远在转的背景。  
   留给「点阵」：图标用固定网格画，选中是点在移动后归位，待机只让个别点慢慢呼吸。

2. **GMUNK，《遗落战境》图形语言**  
   [Oblivion GFX](https://gmunk.com/Oblivion-GFX)  
   [HUDS+GUIS 的整理](https://www.hudsandguis.com/home/2013/05/02/oblivion-interface-design)  
   借：功能先于装饰；一套细线、一种点网格用来对齐，深浅底上都能读；颜色少而统一。点网格是骨架，不是铺满屏幕的科技壁纸。  
   留给「尘迹」和「游标」：线要能单独成立，拿掉所有光晕之后图标还在。

3. **Territory Studio 的近未来界面**  
   [科幻界面和正在出现的技术](https://territorystudio.com/sci-fi-interfaces-and-emerging-technology-4/)  
   [Sci-fi Interfaces 对他们的访谈](https://scifiinterfaces.com/2020/06/23/scifi-interfaces-qa-with-territory-studio/)  
   [David Sheldon-Hicks 谈《她》的做法](https://www.pushing-pixels.org/2014/08/07/the-craft-of-screen-graphics-and-movie-user-interfaces-interview-with-david-sheldon-hicks-of-territory-studio.html)  
   借：近未来要让人觉得是从今天的工具长出来的。调色少、信息能一眼读完。《她》甚至把界面收成声音，剩下的温度来自人和材料，不来自 HUD 边框。  
   这一轮的科幻感只留在终端符号 `>_` 和很慢的粒子上。

4. **Mercury OS（Jason Yuan）**  
   [mercuryos.com](https://www.mercuryos.com/)  
   [介绍文](https://uxdesign.cc/introducing-mercury-os-f4de45a04289)  
   借：对比只出现在需要被看见的地方；动效把注意力送过去，然后停住；字号差本身就是层次。Kiri（雾）是把多余的东西放轻，不是加一层雾效滤镜。  
   留给「尘迹」：底栏几乎没有容器，选中态靠几粒尘和字色，不靠一块色pill。

5. **Material 3 导航栏**  
   [概述](https://m3.material.io/components/navigation-bar/overview)  
   [指南](https://m3.material.io/components/navigation-bar/guidelines)  
   [动效](https://m3.material.io/styles/motion/overview)  
   借：手机竖屏 3 到 5 个目的地、贴底、通栏、无投影；标签一直在；动效短，用减速曲线停住。  
   色块 pill 是现在最常见的选中样式，这一轮不用它当签名。手势条仍然留在最底，避免把系统条误认成选中线。

6. **Apple 人机界面指南里的 Tab Bar**  
   [Tab bars](https://developer.apple.com/design/human-interface-guidelines/tab-bars)  
   借：目的地少、标签可读、快捷动作不要伪装成第五个分页。  
   现有应用已经有一版液态玻璃胶囊。新方案把快捷动作做成另一件物体（圆键、方键或文字按钮），分页本身保持扁平。

7. **Codrops：按钮的粒子**  
   [Particle Effects for Buttons](https://tympanus.net/codrops/2018/04/25/particle-effects-for-buttons/)  
   借：一个明确的动词——散开，再聚回去。粒子数量、寿命、方向都绑在一次点击上，动画结束对象还在。  
   留给「尘迹」的切换：图标在约 400ms 内拆成点再长成下一个图标。静止时仍用矢量描边，避免一直发虚。

8. **Citizen Sleeper 的界面气质**  
   [Curio 的风格记录](https://designbycurio.com/learn/citizen-sleeper-essen-arp)  
   借：科幻可以是墨和纸，炭黑底、一个暖色强调、印刷颗粒。颗粒是纸的材料，不是全息闪点。骰子和时钟是它自己的机制，不拿来用。  
   留给「纸页」：暖底、一根强调色、危险操作单独用红色。

9. **IBM Carbon 象形与 Phosphor**  
   [Carbon pictograms](https://carbondesignsystem.com/elements/pictograms/usage/)  
   [Phosphor](https://phosphoricons.com/)  
   借：同一套网格、同一种线宽，状态用粗细或一个附加零件区分，而不是换一套完全不同的实心底图标。Phosphor 的 regular / bold 可以对应未选中 / 选中。  
   四个概念各自锁一种构造：圆头单线加一粒脱离的点、点阵、直角加方块游标、编辑式细线。

10. **Teenage Engineering**  
    [teenage.engineering](https://teenage.engineering/)  
    借：键和屏幕是两种物体。快捷动作可以是底栏右侧一块单独的键，有自己的边界，按下有轻微的位移，没有发光环。  
    留给「点阵」：长条键床加右侧方键。

11. **Dieter Rams / Vitsœ 的好设计十条**  
    [Good design](https://www.vitsoe.com/us/about/good-design)  
    借：能少则少；材料诚实。底栏不需要自己的纹理才能被认出来。

12. **Playdate**  
    [play.date](https://play.date/)  
    借：限制本身就是风格。1-bit 或低分辨率网格会逼图标变得好认，同时去掉光晕和渐变的位置。  
    留给「点阵」和「游标」：点只有大小两档，游标是实心小方块，不带模糊。

13. **Linear**  
    [linear.app](https://linear.app/)  
    借：控件几乎沉进背景，选中是安静的状态变化。密度来自排版，不来自卡片套卡片。  
    留给「尘迹」和「游标」的页面：大标题、一条分割、数据用字号分开。

14. **Warp**  
    [warp.dev](https://www.warp.dev/)  
    借：终端可以是认真设计过的产品。方块光标、等宽数字只用于数据，界面句子仍用正常黑体。  
    留给「游标」：选中标记就是一枚会眨眼的方块，移动时后面拖几枚逐渐消失的方块。应用图标已经是 `>_`，这条语言和产品是同一件事。

15. **Balmuda**  
    [balmuda.com](https://www.balmuda.com/)  
    借：日系产品站点的留白、大字、一个强调色。二次元在这里取「干净、偏暖、留得住气」的部分，不取角色和贴纸。

16. **现有 SLConsole 的玻璃底栏**  
    代码在 `ui/AppRoot.kt` 的五项 `TABS`，玻璃在 `ui/components/Glass.kt`。  
    借：底栏高度要算进列表 padding；玻璃采样和内容层分开；按下有很轻的缩放。  
    新底栏默认不走 Backdrop。真要采样背景，只在以后单独打开，并且保持高罩色，保证字可读。粒子画在栏上方一块很小的 `Canvas` 里。

## 这些参考怎么分成四条路

| 概念 | 主要参考 | 签名 |
| --- | --- | --- |
| 尘迹 | Mercury、Rams、Codrops、Oblivion 的细线 | 栏几乎消失；图标是圆头单线，每枚图标带一粒脱离的点；切换时线条拆成尘再聚拢 |
| 点阵 | Nothing、Playdate、Teenage Engineering | 两件硬件：键床和右侧方键；图标是 7×7 点；切换时点散开再落位 |
| 游标 | Warp、应用图标 `>_`、Carbon 的统一线宽 | 选中物是方块光标；图标只用直线和方块；光标移动时留下几枚方块残影 |
| 纸页 | Anthropic 中文规范、Citizen Sleeper 的暖墨、Balmuda 的留白 | 暖纸底、衬线标题、橙色只做强调和「操作」；粒子只是纸屑，可关 |

品牌三色在前三个概念里分工：淡紫标示「当前分页」，樱粉标示「快捷动作」，天蓝只出现在偶尔飘过的尘里。纸页不用这三色，改走规范里的陶土橙 `#D97757`，避免两套品牌叠在一张图上。
