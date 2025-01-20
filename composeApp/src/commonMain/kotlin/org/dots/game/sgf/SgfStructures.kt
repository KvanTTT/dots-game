package org.dots.game.sgf

enum class SgfFileFormat(val value: Int) {
    First(1),
    Second(2),
    Third(3),
    Fourth(4);
}

enum class SgfGameMode(val value: Int) {
    Go(1), Othello(2), Chess(3),
    GomokuRenju(4), NineMensMorris(5), Backgammon(6),
    Chinesechess(7), Shogi(8), LinesOfAction(9),
    Ataxx(10), Hex(11), Jungle(12), Neutron(13),
    PhilosophersFootball(14), Quadrature(15), Trax(16),
    Tantrix(17), Amazons(18), Octi(19), Gess(20),
    Twixt(21), Zertz(22), Plateau(23), Yinsh(24),
    Punct(25), Gobblet(26), hive(27), Exxit(28),
    Hnefatal(29), Kuba(30), Tripples(31), Chase(32),
    TumblingDown(33), Sahara(34), Byte(35), Focus(36),
    Dvonn(37), Tamsk(38), Gipf(39), Kropki(40);
}