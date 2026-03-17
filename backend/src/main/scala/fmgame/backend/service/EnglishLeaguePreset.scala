package fmgame.backend.service

/** Preset nazw drużyn dla 4 szczebli ligi angielskiej (92 zespoły: 20+24+24+24).
  * Nazwy w stylu angielskim, generowane losowo z listy (bez licencji na prawdziwe kluby).
  */
object EnglishLeaguePreset {

  val teamNames: List[String] = List(
    "Arsenal", "Aston Villa", "Bournemouth", "Brentford", "Brighton", "Chelsea", "Crystal Palace", "Everton", "Fulham", "Ipswich Town",
    "Leicester City", "Liverpool", "Manchester City", "Manchester United", "Newcastle United", "Nottingham Forest", "Southampton", "Tottenham", "West Ham", "Wolverhampton",
    "Barnsley", "Birmingham City", "Blackburn Rovers", "Bristol City", "Burnley", "Cardiff City", "Coventry City", "Derby County", "Hull City", "Leeds United",
    "Luton Town", "Middlesbrough", "Millwall", "Norwich City", "Oxford United", "Plymouth Argyle", "Portsmouth", "Queens Park Rangers", "Sheffield United", "Stoke City",
    "Sunderland", "Swansea City", "West Bromwich Albion", "Blackpool", "Bolton Wanderers", "Bradford City", "Bristol Rovers", "Burton Albion", "Cambridge United", "Carlisle United",
    "Charlton Athletic", "Chesterfield", "Colchester United", "Exeter City", "Fleetwood Town", "Leyton Orient", "Lincoln City", "Northampton Town", "Peterborough United", "Port Vale",
    "Reading", "Rotherham United", "Shrewsbury Town", "Stevenage", "Wigan Athletic", "Wycombe Wanderers", "Accrington Stanley", "AFC Wimbledon", "Barrow", "Bradford City",
    "Crewe Alexandra", "Doncaster Rovers", "Gillingham", "Grimsby Town", "Harrogate Town", "Mansfield Town", "Morecambe", "Newport County", "Notts County", "Salford City",
    "Stockport County", "Sutton United", "Swindon Town", "Tranmere Rovers", "Walsall",
    "Wrexham", "York City", "Oldham Athletic", "Rochdale", "Scunthorpe United", "Hartlepool United", "Crewe Town"
  )

  val englishLeagueTiers: List[(String, Int)] = List(
    ("Premier League", 20),
    ("Championship", 24),
    ("League One", 24),
    ("League Two", 24)
  )

  def nameForIndex(i: Int): String = teamNames(i % teamNames.size)
}
