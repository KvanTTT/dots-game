package org.dots.game.sgf

import org.dots.game.core.EndGameKind
import org.dots.game.core.Game
import org.dots.game.core.GameResult
import org.dots.game.core.InitPosType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SgfRefinerTests {
    @Test
    fun filterOutEmptyGame() {
        assertNull(
            SgfRefiner.refine(parseConvertAndCheck("(;GM[40]FF[4]SZ[39:32]RU[russian];" +
                "B[tp];W[up];B[uq];W[tq])")))
    }

    @Test
    fun recognizeSingleCross() {
        val refinedGame = check(
            "(;GM[40]FF[4]SZ[39:32]RU[russian];" +
                "B[tp];W[up];B[uq];W[tq];" +
                "B[vq];W[to];B[sp];W[sq])",

            "(;GM[40]FF[4]SZ[39:32]RU[russian]" +
                    "AB[tp][uq]AW[tq][up];" +
                    "B[vq];W[to];B[sp];W[sq])"
        )
        assertEquals(InitPosType.Cross, refinedGame.gameTree.field.rules.initPosType)
    }

    @Test
    fun recognizeDoubleCrossWithGroundingAsConsecutiveMoveWin() {
        val header1 = "(;AP[Спортивные Точки (playdots.ru)]GM[40]FF[4]CA[UTF-8]SZ[39:32]RU[russian]PB[Евгений Жуков]PW[Александр Невский]BR[Кандидат в мастера, 1480]WR[Второй разряд, 1502]OT[20 sec / move]DT[2015-09-19 18:11:55]EV[нерейт]C[без территории, с заземлением, двойной скрест в центре, 20 сек на ход (стандарт), зрители 2, продолжительность 13:50]RE[B+23]SO[https://playdots.ru/game-info/?id=109964]"
        val header2 = "B[vo];W[wr];B[to];W[um];B[un];W[vm];B[tm];W[tl];B[rm];W[qk];B[sl];W[sk];B[rl];W[pl];B[rk];W[sj];B[rj];W[ri];B[si];W[ti];B[sh];W[qi];B[tk];W[tj];B[ul];W[vk];B[uk];W[vj];B[vl];W[wm];B[wl];W[xm];B[xk];W[xl];B[yk];W[wk];B[yn];W[xo];B[xn];W[Am];B[ym];W[Ak];B[yi];W[zg];B[yg];W[yf];B[xg];W[xe];B[xf];W[yd];B[we];W[wd];B[ve];W[vd];B[yh];W[te];B[yj];W[uf];B[vf];W[sf];B[ug];W[tg];B[th];W[rg];B[ue];W[tf];B[ud];W[uc];B[td];W[tc];B[sd];W[rc];B[rd];W[qc];B[qd];W[pd];B[pe];W[oe];B[vc];W[vb];B[wc];W[xd];B[sc];W[sb];B[tb];W[ub];B[rb];W[pf];B[sa];W[pb];B[wb];W[wa];B[xb];W[xa];B[yb];W[ya];B[zb];W[qe];B[za];W[rh];B[zf];W[ye];B[ze];W[zd];B[Ad];W[pg];B[Ac];W[qj];B[yl];W[pn];B[wo];W[om];"

        val refinedGame = check(
            header1 +
                    ";B[sp];W[tp];B[tq];W[sq];W[up];B[vp];W[vq];B[uq];" +
                    header2 +
                    "W[qm][qn][qo][qp][qq][qr][qs][qt][qu][qv][qx][qw][qy][qz][qA][qB][qC][qD][qE][qF][rq])",

            header1 +
                    "AB[sp][tq][uq][vp]AW[sq][tp][up][vq];" +
                    header2 +
                    "B[])"
        )

        val field = refinedGame.gameTree.field
        assertEquals(InitPosType.DoubleCross, field.rules.initPosType)
        val fieldGameResult = field.moveSequence.last() as GameResult.ScoreWin
        assertEquals(EndGameKind.Grounding, fieldGameResult.endGameKind)
        assertEquals(23.0, fieldGameResult.score)
        assertEquals(refinedGame.result, fieldGameResult)
    }

    @Test
    fun groundingAsConsecutiveMoveLoss() {
        val header1 = "(;AP[Спортивные Точки (playdots.ru)]GM[40]FF[4]CA[UTF-8]SZ[39:32]RU[russian]PB[Ярослав ]PW[Костя Захаревич]BR[Первый разряд, 1563]WR[Кандидат в мастера, 1830]TM[300]OT[25 sec / move]DT[2016-11-12 19:54:00]EV[турнир]C[Турнирная игра - Sport Dots 5th Junior Trial 2016, без территории, с заземлением, одинарный скрест в центре, 25 сек на ход, 5 мин на партию (стандарт), зрители 8, средняя оценка 8.0, продолжительность 15:12]"
        val header2 = "SO[https://playdots.ru/game-info/?id=1592550]RO[Групповой этап. Тур 4]AB[tp][uq]AW[up][tq];B[vq];W[vp];B[sp];W[wq];B[vt];W[tr];B[vr];W[xp];B[ws];W[rr];B[sn];W[Ap];B[rm];W[st];B[vw];W[os];B[rj];W[rw];B[wx];W[Bq];B[wA];W[qg];B[nl];W[pl];B[on];W[nm];B[om];W[ol];B[nn];W[mm];B[mn];W[ln];B[lm];W[ml];B[lo];W[kn];B[ko];W[nk];B[my];W[lt];B[mt];W[ms];B[ls];W[ns];B[mu];W[lu];B[lv];W[mv];B[nv];W[mw];B[ou];W[ot];B[nu];W[nw];B[ow];W[pu];B[ov];W[ox];B[jp];W[pw];B[Bi];W[qv];B[Hr];W[jn];B[hp];W[kl];B[Fw];W[iu];B[en];W[nh];B[gf];W[pd];B[pg];W[qh];B[qf];W[pf];B[og];W[pe];B[rf];W[pi];B[nf];W[sg];B[se];W[th];B[ve];W[oi];B[lf];W[qn];B[wk];W[qm];B[qp];W[pp];B[po];W[qo];B[op];W[pq];B[rp];W[ul];B[tj];W[sk];B[tk];W[tl];B[sl];W[rl];B[sm];W[rk];B[vn];W[un];B[uo];W[vo];B[um];W[to];B[tm];W[so];B[fw];W[vm];B[sB];W[oB];B[oC];W[iA];B[jz];W[gx];B[hx];W[gw];B[hw];W[hv];B[gv];W[hy];B[gy];W[fx];B[iy];W[hz];B[jx];W[jA];B[lz];W[lB];B[oz];W[pz];B[mB];W[mA];B[lA];W[kB];B[nB];W[qC];B[pB];W[qB];B[qA];W[pA];B[oA];W[rA];B[qz];W[py];B[rz];W[sz];B[sA];W[rB];B[qE];W[pD];B[pE];W[oD];B[mE];W[nE];B[nF];W[mD];B[oE];W[nD];B[lE];W[lC];B[iE];W[fA];B[cB];W[fD];B[fE];W[gE];B[gD];W[hE];B[hD];W[iD];B[iC];W[jD];B[fz];W[fy];B[gz];W[ez];"

        check(
            header1 + "RE[W+93]" + header2 + "W[bB][dB][cC][cA][dn][fn][eo][em][ew][fv][gA][eE][fF][ff][hf][gg][ge][gu][gC][hC][gp][ip][hq][ho][iw][ix][jy][iz][jC][iB][jE][iF][kp][jq][jo][kx][jw][kz][mo][lp][kf][mf][lg][le][km][ll][ks][lr][kv][kE][lF][lD][mF][no][pn][oo][of][ng][ne][np][oq][oF][pF][rE][qF][qD][qe][sf][rg][re][qq][rq][ro][sq][qy][ry][qj][sj][ri][te][sd][tA][tB][sC][uj][ti][uk][ur][wr][vs][ue][we][vf][vd][wn][ut][wt][vu][uw][ww][vx][vv][vk][xk][wl][wj][xs][xx][wy][vA][xA][wB][wz][Ai][Ci][Bj][Bh][Ew][Gw][Fx][Fv][Gr][Ir][Hs][Hq])",
            header1 + "RE[W+R]" + header2 + "B[resign])"
        )
    }

    @Test
    fun groundingAsAlternatingMoveWin() {
        val header1 = "(;AP[Спортивные Точки (playdots.ru)]GM[40]FF[4]CA[UTF-8]SZ[39:32]RU[russian]PB[Михаил Марлов]PW[Дмитрий Саликов]BR[Второй разряд, 1510]WR[Первый разряд, 1544]TM[360]OT[30 sec / move]DT[2016-06-21 23:03:44]EV[турнир]C[Турнирная игра - Sport Dots 1st Junior Trial 2016, без территории, с заземлением, одинарный скрест в центре, 30 сек на ход, 6 мин на партию (стандарт), зрители 8, средняя оценка 10.0, продолжительность 37:22]"
        val header2 = "SO[https://playdots.ru/game-info/?id=1213317]RO[Групповой этап. Тур 5]AB[tp][uq]AW[up][tq];B[ur];W[tr];B[us];W[vp];B[st];W[sp];B[to];W[rq];B[un];W[wo];B[rn];W[pq];B[ts];W[op];B[tj];W[yo];B[yt];W[Ao];B[Ds];W[Dn];B[Cn];W[Co];B[Do];W[Cm];B[Bn];W[Bo];B[Dm];W[En];B[Bm];W[Cl];B[Bl];W[Bk];B[Ck];W[Dl];B[Ak];W[Bj];B[zl];W[Aj];B[zj];W[zi];B[yj];W[xi];B[yi];W[yh];B[zh];W[yk];B[zk];W[Ai];B[xh];W[yg];B[xj];W[wi];B[wj];W[vj];B[vi];W[wh];B[vk];W[uj];B[uk];W[ui];B[tl];W[Dp];B[Eo];W[Eq];B[Em];W[Fn];B[Fp];W[Fq];B[Gp];W[Gq];B[Hp];W[Hq];B[Fl];W[Dk];B[Gm];W[Ir];B[Cj];W[Ej];B[Hn];W[Ci];B[xg];W[wg];B[xf];W[vf];B[Ah];W[Di];B[zf];W[Ip];B[Io];W[ue];B[zp];W[zo];B[Jp];W[Jr];B[Iq];W[Kr];B[pt];W[no];B[os];W[nr];B[mt];W[lr];B[kt];W[jr];B[it];W[hr];B[ft];W[fq];B[dq];W[eo];B[ep];W[fp];B[fo];W[fn];B[go];W[gn];B[do];W[en];B[dn];W[dm];B[em];W[fl];B[el];W[gk];B[ek];W[fj];B[fk];W[hl];B[gj];W[hj];B[gi];W[hi];B[gh];W[hh];B[fm];W[gl];B[gm];W[hn];B[hm];W[im];B[il];W[hk];B[jl];W[jm];B[kl];W[km];B[ll];W[nl];B[lm];W[kn];B[ln];W[ko];B[nm];W[om];B[nn];W[on];B[ol];W[ok];B[pl];W[mk];B[kj];W[li];B[jh];W[ig];B[ki];W[jg];B[kg];W[kf];B[lg];W[lf];B[mg];W[nf];B[ng];W[of];B[og];W[pg];B[ph];W[oh];B[oi];W[nh];B[ni];W[mh];B[lj];W[mf];B[mj];W[qh];B[qi];W[rh];B[ri];W[si];B[sj];W[rj];B[qj];W[rk];B[ti];W[sh];B[qk];W[rl];B[qm];W[mn];B[mm];W[mo];B[sm];W[er];B[dr];W[ds];B[es];W[gt];B[gu];W[ht];B[hu];W[cr];B[cn];W[cm];B[bn];W[ej];B[dk];W[cj];B[bl];W[ck];B[cl];W[gq];B[fu];W[nt];B[ns];W[ms];B[lt];W[nu];B[qt];W[iu];B[ju];W[iv];B[ru];W[is];B[jt];W[mv];B[um]"

        check(
            header1 + "RE[B+121]" + header2 + ";W[vh];B[bj][dj][ci][bk][bm][br][cs][cq][dt][ei][fi][ik][ij][ii][ih][hg][if][jf][hv][jv][iw][ke][le][me][ne][pf][oe][lv][nv][mw][mu][ot][ou][qg][rg][th][sg][te][ve][uf][ud][uh][vg][wf][uo][wp][vq][vo][xo][wn][zg][yf][xk][yl][yp][yn][zn][Ap][An][Bp][Cp][Bi][Ei][Dh][Ch][Ek][Fj][Ep][Dq][Er][Fr][Gr][Hr][Is][Js][Jq][Lr][Ks][Kq])",
            header1 + "RE[B+R]" + header2 + ";W[resign])"
        )
    }

    @Test
    fun groundingAsAlternatingMoveLoss() {
        val header1 = "(;AP[Спортивные Точки (playdots.ru)]GM[40]FF[4]CA[UTF-8]SZ[39:32]RU[russian]PB[Владимир Владимирович]PW[Евгений Лякишев]BR[Первый разряд, 1701]WR[Первый разряд, 1600]TM[600]OT[30 sec / move]DT[2017-05-15 21:28:50]EV[турнир]C[Турнирная игра - Sport Dots Spring Open Cup 2017, без территории, с заземлением, одинарный скрест в центре, 30 сек на ход, 10 мин на партию (стандарт), зрители 20, средняя оценка 10.0, продолжительность 01:18:35]"
        val header2 = "SO[https://playdots.ru/game-info/?id=2089986]RO[1/32 финала]AB[tp][uq]AW[up][tq];B[to];W[vo];B[vr];W[vx];B[ws];W[sr];B[sn];W[wn];B[sl];W[rs];B[vv];W[tw];B[su];W[tu];B[tt];W[rv];B[ru];W[st];B[tv];W[qu];B[uu];W[qt];B[ys];W[wk];B[wx];W[vj];B[tk];W[ug];B[ri];W[wC];B[wz];W[tA];B[qv];W[pv];B[qw];W[ou];B[px];W[sw];B[xA];W[vy];B[wy];W[yD];B[vB];W[uC];B[AA];W[zB];B[Bz];W[uz];B[wB];W[tB];B[nx];W[lu];B[my];W[CE];B[DA];W[FC];B[Fz];W[IA];B[Ix];W[Lx];B[Kv];W[Jw];B[Jz];W[KA];B[JA];W[KB];B[JB];W[JC];B[IC];W[ID];B[JD];W[KC];B[HD];W[IE];B[HC];W[GE];B[EE];W[FE];B[DE];W[DC];B[ED];W[EC];B[FD];W[GD];B[GC];W[CD];B[Ky];W[Ly];B[Kw];W[Lu];B[Js];W[Kt];B[Jt];W[Lq];B[Io];W[Kq];B[Iq];W[Jn];B[Hm];W[Il];B[Gk];W[Gg];B[Hi];W[Hj];B[Fi];W[Gi];B[Jo];W[Kn];B[Im];W[Jl];B[Jm];W[Km];B[Gh];W[Gj];B[Eh];W[Fj];B[Bk];W[Dj];B[Aj];W[Em];B[In];W[Bn];B[Cm];W[Cn];B[Dn];W[Cl];B[Bl];W[Bm];B[Dm];W[Bi];B[Ai];W[Dk];B[zn];W[Ah];B[zh];W[zi];B[Bh];W[Bj];B[Ag];W[Ak];B[Al];W[zk];B[Ck];W[Cj];B[Dl];W[xl];B[Ii];W[Hh];B[Fh];W[Ik];B[Lt];W[Ks];B[Lv];W[Mv];B[Ku];W[Mu];B[Kr];W[Ls];B[Lr];W[Mt];B[Mr];W[Jr];B[Jq];W[Ir];B[Hr];W[Kx];B[Is];W[Jx];B[Iy];W[Iv];B[Ju];W[Gv];B[Gy];W[Hu];B[Eo];W[Es];B[Dq];W[Bs];B[Aq];W[Bq];B[Bp];W[Ar];B[zq];W[zr];B[yq];W[Ap];B[Ao];W[yr];B[xq];W[vq];B[ur];W[Cp];B[Bo];W[Co];B[Cq];W[xr];B[wq];W[Br];B[zp];W[vp];B[wr];W[ww];B[vw];W[xv];B[yu];W[yw];B[zw];W[yx];B[xC];W[yB];B[vC];W[yz];B[yA];W[zA];B[zz];W[zy];B[Az];W[zv];B[xx];W[yy];B[xw];W[wv];B[yv];W[Aw];B[wu];W[Au];B[wD];W[AC];B[xt];W[Dv];B[Ep];W[uE];B[wF];W[El];B[Gl];W[Ll];B[Jh];W[Ig];B[Jg];W[If];B[Lg];W[Id];B[Cd];W[vg];B[Bc];W[vd];B[rg];W[kv];B[mA];W[ux];B[xE];W[qr];B[sf];W[uc];B[rc];W[zj];B[sd];W[Ax];B[lB];W[hv];B[lD];W[tE];B[Ds];W[Dt];B[Cs];W[Ct];B[Er];W[Eu];B[ew];W[gu];B[et];W[gy];B[fz];W[hA];B[gB];W[iC];B[hB];W[gA];B[fA];W[iB];B[iA];W[jA];B[iz];W[hz];B[hy];W[jz];B[gz];W[kB];B[jy];W[ky];B[kz];W[jx];B[iy];W[lz];B[mz];W[lA];B[mC];W[gx];B[ix];W[jw];B[iw];W[iv];B[ow];W[nu];B[hD];W[jE];B[kD];W[jD];B[Cg];W[Ek];B[zm];W[mE];B[pE];W[nD];B[pC];W[oC];B[qB];W[nB];B[mB];W[pB];B[qC];W[pA];B[qA];W[pD];B[qD];W[oD];B[rz];W[qE];B[rE];W[qF];B[rF];W[pF];B[qy];W[HE];B[sD];W[uD];B[ey];W[gw];B[yi];W[yk];B[xh];W[vh];B[xj];W[vl];B[vn];W[un];B[vm];W[um];B[tm];W[wm];B[xk];W[yl];B[vi];W[ui];B[wi];W[uj];B[vb];W[ub];B[ve];W[ue];B[vf];W[uf];B[wf];W[BD];B[yC];W[KE];B[zD];W[At];B[yE];W[Lz];B[Jc];W[Ke];B[Je];W[Hc];B[Ic];W[Gd];B[Hb];W[Gb];B[Ga];W[Ib];B[Ha];W[Jf];B[Jd];W[Kf];B[Kg];W[Ie];B[Jb];W[Lf];B[Mf];W[Me];B[Fb];W[Gc];B[Dg];W[Ia];B[Lo];W[Lp];B[Ko];W[Ml];B[lx];W[mu];B[lv];W[ku];B[lw];W[Fu];B[Be];W[wg];B[xg];W[wb];B[va];W[xf];B[we];W[xe];B[wd];W[xd];B[wc];W[xc];B[vc];W[ud];B[xb];W[yb];B[wa];W[zc];B[ya];W[za];B[qb];W[ua];B[kw];W[jv];B[kx];W[Fn];B[Gr];W[jl];B[gk];W[fo];B[fs];W[kq];B[dn];W[dp];B[gr];W[hq];B[gq];W[gp];B[ep];W[fl];B[fp];W[ho];B[eo];W[fn];B[dl];W[ek];B[dk];W[ji];B[ej];W[ih];B[fj];W[ie];B[hl];W[fk];B[hj];W[jk];B[im];W[jm];B[in];W[kn];B[jo];W[ko];B[jp];W[ir];B[em];W[iq];B[fm];W[sh];B[rh];W[sj];B[rj];W[sk];B[rk];W[ti];B[tl];W[tn];B[so];W[rl];B[sm];W[ql];B[om];W[qn];B[qp];W[po];B[op];W[pp];B[kp];W[pq];B[lp];W[jd];B[mo];W[pm];B[nn];W[oo];B[np];W[jq];B[io];W[hp];B[gn];W[go];B[hn];W[ig];B[ib];W[fe];B[ed];W[jb];B[ja];W[ic];B[hb];W[fc];B[hc];W[df];B[hd];W[fg];B[he];W[hf];B[ge];W[gf];B[fd];W[ee];B[de];W[ce];B[dd];W[bf];B[id];W[je];B[jc];W[fb];B[cc];W[fa];B[kb];W[oE];B[ol];W[co];B[cn];W[bp];B[am];W[af];B[fv];W[gv];B[eu];W[uh];B[ex];W[is];B[ii];W[ij];B[hi];W[jh];B[ap];W[ao];B[bn];W[bo];B[Bb];W[zb];B[Ca];W[yc];B[Cf];W[Le];B[se];W[Hd];B[qa];W[JF];B[qc];W[LD];B[rd];W[Hg];B[sg];W[th];B[Mg];W[Ij];B[Ba];W[Kl];B[Ce];W[Mp];B[Bd];W[Et];B[Fs];W[uF];B[Jp];W[uB];B[vz];W[tz];B[uw];W[tx];B[rw];W[sv];B[rt];W[ss];B[ov];W[pu];B[hs];W[it];B[gs];W[mF];B[ft];W[iD];B[rD];W[jB];B[mD];W[iu];B[hC];W[oB];B[kE];W[jF];B[DF];W[km];B[pk];W[cp];B[qk];W[jj];B[kd];W[if];B[ke];W[ef];B[kf];W[ff];B[kg];W[cf];B[kh];W[uy];B[ki];W[wl];B[no];W[Ej];B[lj];W[qs]"

        check(
            header1 + "RE[W+8]" + header2 + ";B[En][Gn][Fo][Fm])",
            header1 + "RE[W+R]" + header2 + ";B[resign])"
        )
    }

    @Test
    fun groundingAsSeparatedMoves() {
        val header1 =
            "(;FF[4]GM[40]CA[UTF-8]AP[zagram.org]"
        val header2 =
            "SZ[39:32]RU[Punish=0,Holes=1,AddTurn=0,MustSurr=1,MinArea=1,Pass=0,Stop=1,LastSafe=0,ScoreTerr=0,InstantWin=0]AB[sp][tq]AW[sq][tp]PB[Ivp2006]PW[Saraskin]TM[60]OT[0+7]BL[75]WL[72]DT[2023-06-09]BR[1083]WR[1222];B[so]BL[67];W[up]WL[67];B[tr]BL[67];W[rq]WL[67];B[ss]BL[67];W[qr]WL[67];B[tn]BL[67];W[vq]WL[67];B[tv]BL[67];W[or]WL[67];B[sk]BL[67];W[xq]WL[67];B[sw]BL[67];W[pu]WL[67];B[tj]BL[67];W[wt]WL[67];B[st]BL[67];W[ks]WL[67];B[sh]BL[67];W[Br]WL[67];B[ty]BL[67];W[sc]WL[67];B[wk]BL[67];W[Ci]WL[67];B[ol]BL[67];W[hj]WL[67];B[ye]BL[67];W[zb]WL[67];B[me]BL[67];W[lb]WL[67];B[xb]BL[67];W[xd]WL[67];B[yd]BL[67];W[yc]WL[67];B[xc]BL[67];W[xe]WL[67];B[xf]BL[67];W[yf]WL[67];B[wd]BL[66.5];W[we]WL[67];B[ve]BL[66.5];W[wf]WL[67];B[xg]BL[62.1];W[ze]WL[67];B[tc]BL[59.7];W[vd]WL[67];B[wc]BL[59.7];W[vf]WL[67];B[ue]BL[59.7];W[uf]WL[67];B[te]BL[59.7];W[tf]WL[67];B[sd]BL[59.7];W[se]WL[67];B[rd]BL[59.6];W[rf]WL[67];B[lk]BL[59.6];W[qe]WL[65.8];B[qc]BL[59.5];W[pf]WL[65.8];B[kh]BL[59.5];W[pc]WL[65.8];B[sb]BL[59.5];W[qd]WL[65.8];B[rb]BL[59.5];W[ob]WL[65.8];B[kn]BL[59.5];W[ht]WL[65.8];B[hm]BL[59.5];W[wi]WL[65.8];B[wh]BL[59.5];W[vh]WL[65.8];B[xi]BL[59.5];W[xj]WL[65.8];B[yg]BL[59.5];W[zd]WL[65.8];B[wj]BL[59.5];W[vi]WL[65.8];B[zg]BL[59.5];W[wg]WL[65.8];B[xh]BL[59.5];W[xk]WL[65.8];B[Af]BL[59.5];W[uk]WL[65.8];B[vl]BL[59.5];W[uj]WL[65.3];B[ul]BL[59.5];W[tl]WL[61.6];B[um]BL[59.5];W[sm]WL[60.9];B[zl]BL[59.5];W[yl]WL[60.9];B[ym]BL[59.5];W[wl]WL[60.9];B[vk]BL[59.5];W[zk]WL[60.9];B[Al]BL[59.5];W[xm]WL[60.9];B[qo]BL[54.3];W[yn]WL[60.9];B[zm]BL[54.3];W[tk]WL[60.9];B[wm]BL[54.3];W[xl]WL[60.9];B[wn]BL[54.3];W[xn]WL[60.9];B[pn]BL[54.3];W[xp]WL[60.9];B[eu]BL[54.3];W[Cd]WL[60.9];B[Df]BL[53.6];W[Ee]WL[60.9];B[Eg]BL[53.6];W[Hf]WL[60.9];B[Gg]BL[53.6];W[Ih]WL[60.9];B[Fk]BL[53.6];W[Gi]WL[60.9];B[Fe]BL[53.6];W[Gd]WL[60.9];B[Fi]BL[53.6];W[Fh]WL[60.3];B[Gh]BL[53.6];W[Gj]WL[59.1];B[Fj]BL[53.6];W[Eh]WL[59.1];B[Di]BL[53.6];W[Dh]WL[59.1];B[Ch]BL[53.6];W[Bg]WL[55.4];B[Cg]BL[52.7];W[Bf]WL[55.4];B[Bh]BL[52.7];W[Ah]WL[55.4];B[Ag]BL[52.7];W[Cf]WL[52.1];B[Ai]BL[50.8];W[Dg]WL[52.1];B[Bi]BL[47];W[Ef]WL[52.1];B[De]BL[47];W[Fg]WL[52.1];B[Cj]BL[47];W[Im]WL[50.4];B[Eo]BL[47];W[Gq]WL[50.4];B[Dr]BL[47];W[Dq]WL[50.4];B[Ho]BL[47];W[Gm]WL[50.4];B[Fn]BL[47];W[Ip]WL[50.4];B[Eq]BL[47];W[Er]WL[50.4];B[Dp]BL[47];W[Cq]WL[50.4];B[Fr]BL[47];W[Es]WL[50.4];B[Fq]BL[47];W[Fs]WL[50.4];B[Gr]BL[47];W[Is]WL[50.4];B[Hs]BL[47];W[Ht]WL[50.4];B[Ir]BL[47];W[Gs]WL[50.4];B[Hr]BL[47];W[Jr]WL[50.4];B[Jq]BL[47];W[Kr]WL[50.4];B[Kp]BL[47];W[Lp]WL[50.4];B[Lo]BL[47];W[Ko]WL[50.4];B[Jo]BL[47];W[Kn]WL[50.4];B[Lq]BL[47];W[Kq]WL[36.9];B[Mp]BL[47];W[Jp]WL[36.9];B[Io]BL[47];W[Iq]WL[36.9];B[Hq]BL[46.2];W[Hk]WL[35.1];B[It]BL[46.2];W[Js]WL[35.1];B[Gt]BL[46.2];W[Hu]WL[35.1];B[Ds]BL[46.2];W[Fu]WL[35.1];B[Hl]BL[41.2];W[Il]WL[35.1];B[Ej]BL[41.2];W[Ed]WL[32.8];B[Iu]BL[41.2];W[Gu]WL[32.8];B[Lt]BL[41.2];W[Ju]WL[32.8];B[Iv]BL[41.2];W[Ku]WL[32.8];B[Lv]BL[41.2];W[Kw]WL[32.8];B[Ik]BL[41.2];W[Gl]WL[32.8];B[Gk]BL[41.2];W[Hj]WL[32.8];B[Kv]BL[41.2];W[Jv]WL[32.8];B[Jw]BL[41.2];W[Iw]WL[32.8];B[Hv]BL[41.2];W[Jx]WL[32.8];B[Ct]BL[41.2];W[Ae]WL[32.8];B[Ev]BL[41.2];W[Jm]WL[32.8];B[Du]BL[41.2];W[zn]WL[26.9];B[Gw]BL[41.2];W[Em]WL[26.9];B[Bn]BL[41.2];W[Co]WL[26.9];B[Dn]BL[41.2];W[Cn]WL[26.9];B[Dm]BL[41.2];W[Cm]WL[26.9];B[Dl]BL[41.2];W[Ak]WL[26.9];B[Bl]BL[41.2];W[Cl]WL[26.9];B[Bk]BL[41.1];W[Dk]WL[25.2];B[Ck]BL[40.5];W[Bp]WL[25.2];B[El]BL[40.5];W[Ao]WL[25.2];B[ky]BL[36.7];W[za]WL[25.2];B[gw]BL[36.7];W[yb]WL[25.2];B[fr]BL[36.7];W[Et]WL[25.2];B[Fw]BL[36.7];W[Jt]WL[25.2];B[py]BL[36.7];W[Dd]WL[25.2];B[sj]BL[36.7];W[ui]WL[25.2];B[gi]BL[36.7];W[Be]WL[25.2];B[Jk]BL[36.7];W[Kj]WL[25.2];B[Kk]BL[36.7];W[Lk]WL[25.2];B[Ll]BL[36.7];W[Kl]WL[25.2];B[Lj]BL[36.7];W[Mk]WL[25.2];B[Ki]BL[36.7];W[Jj]WL[25.2];B[Lu]BL[36.7];W[Ji]WL[25.2];B[nz]BL[36.7];W[Li]WL[25.2];B[fk]BL[36.7];W[Mj]WL[25.2];B[ih]BL[36.7];W[xo]WL[25.1];B[ix]BL[36.7];W[wq]WL[25.1];B[go]BL[36.7];W[uq]WL[25.1];B[mg]BL[36.7];W[wr]WL[25.1];B[gl]BL[36.7];W[pd]WL[25.1];B[rx]BL[36.7];W[oa]WL[25.1];B[re]BL[36.7];W[sf]WL[25.1];B[vc]BL[36.7];W[qf]WL[25.1];B[si]BL[36.7];W[oc]WL[25.1];B[ub]BL[36.7];W[Fc]WL[25.1];B[tu]BL[36.7];W[Bq]WL[25.1];B[qy]BL[36.7];W[Kh]WL[25.1];B[Dt]BL[36.7];W[Ig]WL[25.1];B[Ml]BL[36.7];W[He]WL[25.1];B[mz]BL[36.7];W[la]WL[25.1];B[et]BL[36.7];W[Lr]WL[25.1];B[gn]BL[36.7];W[sl]WL[25.1];B[rk]BL[36.7];W[Mr]WL[25.1];B[ro]BL[36.7];W[Lw]WL[25.1];B[fq]BL[36.7];W[Mw]WL[25.1];B[es]BL[36.7]C[W pressed STOP];"

        check(
            header1 + "RE[W+R]" + header2 +
                    "B[hi]BL[36.7];B[ij]BL[36.7];B[hk]BL[36.7];B[gj]BL[36.7];B[hs]BL[36.7];B[it]BL[36.7];B[hu]BL[36.7];B[gt]BL[36.7];B[kr]BL[36.7];B[ls]BL[36.7];B[kt]BL[36.7];B[js]BL[36.7];B[oq]BL[36.7];B[pr]BL[36.7];B[os]BL[36.7];B[nr]BL[36.7];B[pt]BL[36.7];B[qu]BL[36.7];B[pv]BL[36.7];B[ou]BL[36.7];B[qq]BL[36.7];B[rr]BL[36.7];B[qs]BL[36.7];B[rp]BL[36.7];B[ws]BL[36.7];B[xt]BL[36.7];B[wu]BL[36.7];B[vt]BL[36.7];B[fv]BL[241.7];B[lz]BL[229.2];B[mf]BL[226.5];B[ji]BL[225.2];B[Gv]BL[223.9];B[Fv]BL[222.9];B[Lm]BL[220.2];B[Ln]BL[219.5])",

            header1 + "RE[W+52]" + header2 +
                    "W[])"
        )
    }

    @Test
    fun notagoGroundingWin() {
        val header = "(;FF[4]GM[40]CA[UTF-8]AP[notAgo:4.2.4]PC[https://t.me/notAgo]DT[2023-09-05]SO[1693942648]PB[Utsux]BR[2614]PW[Test]WR[2208]RU[Особый]SZ[39:32]RE[B+G]TM[300]OT[30 sec / move]AB[tp][uq]AW[up][tq];B[vq]BL[300];W[uo]WL[300];B[sp]BL[300];W[tr]WL[300];B[vs]BL[300];W[tt]WL[300];B[sn]BL[300];W[vn]WL[300];B[po]BL[300];W[xn]WL[300];B[yr]BL[300];W[ss]WL[300];B[Bm]BL[300];W[wk]WL[300];B[vh]BL[300];W[tk]WL[300];B[qi]BL[300];W[Ao]WL[300];B[yo]BL[300];W[zk]WL[300];B[zn]BL[300];W[yh]WL[300];B[sh]BL[300];W[Bg]WL[300];B[rl]BL[300];W[xl]WL[300];B[xp]BL[300];W[sw]WL[300];B[ui]BL[300];W[ty]WL[300];B[En]BL[300];W[Bi]WL[300];B[Fs]BL[300];W[Fh]WL[300];B[rj]BL[300];W[Dd]WL[300];B[te]BL[300];W[zd]WL[300];B[Ho]BL[300];W[Ie]WL[300];B[sc]BL[300];W[Gb]WL[300];B[Is]BL[300];W[sA]WL[300];B[Cn]BL[300];W[su]WL[300];B[rm]BL[300];W[tC]WL[300];B[Ip]BL[300];W[sE]WL[300];B[Kp]BL[300];W[sv]WL[300];B[Am]BL[300];W[sx]WL[300];B[si]BL[300];W[sC]WL[300];B[tf]BL[300];W[Bb]WL[300];B[td]BL[300];W[zi]WL[300];B[Gn]BL[300];W[zj]WL[300];B[Dn]BL[300];W[yk]WL[300];B[wq]BL[300];W[yl]WL[300];B[rk]BL[300];W[xm]WL[300];B[ti]BL[300];W[wn]WL[300];B[xr]BL[300];W[un]WL[300];B[vr]BL[300];W[Ai]WL[300];B[Jp]BL[300];W[Bh]WL[300];B[Lp]BL[300];W[Bf]WL[300];B[Mp]BL[300];W[Be]WL[300];B[Fn]BL[300];W[Bd]WL[300];B[so]BL[300];W[Bc]WL[300];B[sg]BL[300];W[Ba]WL[300];B[sb]BL[300];W[st]WL[300];B[qn]BL[300];W[ts]WL[300];B[qo]BL[300];W[sy]WL[300];B[rn]BL[300];W[sz]WL[300];B[ri]BL[300];W[sB]WL[300];B[tg]BL[300];W[sD]WL[300];B[vi]BL[300];W[sF]WL[300];B[tc]BL[300];W[vk]WL[300];B[sa]BL[300];W[uk]WL[300];B[Hp]BL[300];W[zh]WL[300];B[Go]BL[300];W[Eh]WL[300];B[Bo]BL[300];W[Dh]WL[300];B[Ap]BL[300];W[Ch]WL[300];B[zq]BL[300];W[Cd]WL[300];B[Ir]BL[300];W[Ad]WL[300];B[Iq]BL[300];W[Fb]WL[300];B[Gs]BL[300];W[Eb]WL[300];B[Hs]BL[300];W[Db]WL[300]"

        check(
            "$header)",
            "$header;B[])"
        )
    }

    @Test
    fun notagoGroundingLoss() {
        val header1 = """
(;FF[4]GM[40]CA[UTF-8]

AP[notAgo:4.2.4]
PC[https://t.me/notAgo]
DT[2023-09-02]
SO[1693648027]

PB[timostar]BR[1000]
PW[Test]WR[2271]

RU[Особый]
SZ[39:32]
"""

        val header2 =
"""
TM[240]
OT[20 sec / move]

AB[qn][po][wt][vu][ru][qv][wl][vm]
AW[pn][qo][vt][wu][qu][rv][vl][wm]

;B[um]BL[240];W[ul]WL[240];B[tm]BL[240];W[pp]WL[240];B[oo]BL[240];W[qq]WL[240];B[qm]BL[240];W[pm]WL[240];B[ro]BL[240];W[qp]WL[240];B[pl]BL[240];W[om]WL[240];B[ol]BL[240];W[nm]WL[240];B[np]BL[240];W[or]WL[240];B[su]BL[240];W[sv]WL[240];B[uu]BL[240];W[ut]WL[240];B[tt]BL[240];W[tv]WL[240];B[tu]BL[240];W[ws]WL[240];B[xt]BL[240];W[vr]WL[240];B[vw]BL[240];W[wn]WL[240];B[ur]BL[240];W[wq]WL[240];B[ts]BL[240];W[vs]WL[240];B[vp]BL[240];W[wp]WL[240];B[tq]BL[240];W[sp]WL[240];B[rp]BL[240];W[rq]WL[240];B[sq]BL[240];W[to]WL[240];B[so]BL[207.87];W[tp]WL[207.87];B[tn]BL[207.87];W[uo]WL[207.87];B[vo]BL[207.87];W[ql]WL[207.87];B[rl]BL[207.87];W[qk]WL[207.87];B[rk]BL[207.87];W[rj]WL[207.87];B[sj]BL[207.87];W[ri]WL[207.87];B[tk]BL[207.87];W[vj]WL[207.87];B[vk]BL[207.87];W[wo]WL[207.87];B[vn]BL[207.87];W[th]WL[207.87];B[uj]BL[207.87];W[vi]WL[207.87];B[yl]BL[207.87];W[Ao]WL[207.87];B[yi]BL[207.87];W[Af]WL[207.87];B[Ck]BL[207.87];W[Ak]WL[207.87];B[Al]BL[207.87];W[Bl]WL[207.87];B[Bk]BL[207.87];W[Aj]WL[207.87];B[Am]BL[207.87];W[Bm]WL[207.87];B[Bn]BL[207.87];W[Cm]WL[207.87];B[Cn]BL[207.87];W[Dm]WL[207.87];B[An]BL[207.87];W[Eo]WL[207.87];B[Ah]BL[207.87];W[yj]WL[207.87];B[xj]BL[207.87];W[zi]WL[207.87];B[yh]BL[207.87];W[zh]WL[207.87];B[zg]BL[207.87];W[yg]WL[207.87];B[Ai]BL[207.87];W[zj]WL[207.87];B[Bj]BL[191.71];W[xi]WL[191.71];B[xh]BL[191.71];W[xk]WL[191.71];B[wi]BL[191.71];W[wk]WL[191.71];B[yk]BL[191.71];W[xl]WL[191.71];B[wj]BL[173.37];W[xm]WL[173.37];B[ui]BL[173.37];W[vh]WL[173.37];B[uh]BL[173.37];W[wg]WL[173.37];B[zm]BL[173.37];W[ug]WL[173.37];B[vq]BL[173.37];W[sh]WL[173.37];B[xs]BL[173.37];W[wr]WL[173.37];B[yp]BL[173.37];W[yq]WL[173.37];B[yr]BL[173.37];W[zo]WL[173.37];B[yo]BL[173.37]
        """.trimIndent()

        check(
            "${header1}\nRE[B+G]\n${header2})",
            "${header1}\nRE[B+R]\n${header2};W[resign])"
        )
    }

    @IgnorableReturnValue
    private fun check(sgf: String, expectedRefinedSgf: String): Game {
        val refinedGames = SgfRefiner.refine(parseConvertAndCheck(sgf))!!
        assertEquals(expectedRefinedSgf, SgfWriter.write(refinedGames))
        return refinedGames.single()
    }
}