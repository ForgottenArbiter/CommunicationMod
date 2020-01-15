## Changelog ##

#### v0.8.0 ####
* Added card_in_play to the game state
* Added the turn number to the game state
* Added the number of cards discarded this turn to the game state
* Added the monsters' last two move ids to the game state
* Fixed crash with StS version 1.1
* Fixed a bug where max energy would be transmitted instead of current energy

#### v0.7.0 ####
* Added Limbo to the game state, which is used for various cards such as Havoc
* Added a number of new fields to specific powers which did not have all of their state captured

#### v0.6.0 ####
* Added "act_boss" to the game state, indicating the first boss to be fought in the current Act
* Made Communication Mod compatible with Slay the Spire v1.1

#### v0.5.0 ####
* Added "any_number" to the grid select screen state, indicating whether any number of cards can be selected
* Fixed "choose boss" rarely failing to select the boss node
* Communication Mod now waits for more rest actions to finish before becoming ready, fixing a number of related issues

#### v0.4.0 ####
* Initial public release