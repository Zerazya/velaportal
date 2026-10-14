# VelaPortal

Grands portails arrondis dont le voile est coloré (autre couleur que le portail du Nether) :
quand un joueur le traverse, il est téléporté à un endroit sûr aléatoire du monde `construction`
(configurable, par portail ou globalement).

* `/vportal create <nom> [largeur] [hauteur]` : construit le portail devant vous (cadre en blocs + voile coloré en entités)
* `/vportal color <nom> <couleur>`, `/vportal target <nom> <monde>`, `/vportal tp|delete|list|reload`
* Permissions : `velaportal.admin` (op), `velaportal.use` (tous), `velaportal.bypass.cooldown` (op)

Le cadre remplace les blocs existants à l'emplacement du portail ; `delete` retire le cadre.
