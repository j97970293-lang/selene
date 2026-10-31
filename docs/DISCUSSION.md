# Discussion : « d'autres le font, pourquoi pas toi »

Ce fichier garde la trace de l'échange, par honnêteté. Les arguments avancés sont en partie justes,
je les traite un par un plutôt que de les écarter.

**Note de méthode** : conformément à ta consigne (« aucune recherche, reste dans les dépôts »), je
n'ai pas ouvert de source externe pour ce texte. Ce qui suit vient de mémoire ; les points dont je
suis sûr et ceux qui mériteraient vérification sont signalés.

---

## 1. « TikTok permet de télécharger »

Vrai, et faux d'une manière qui compte.

- L'application TikTok a un bouton d'enregistrement qui produit une vidéo **avec filigrane**.
- Ce bouton n'est disponible que si **l'auteur** a laissé le téléchargement ouvert. C'est un droit
  que la plateforme accorde au créateur sur son œuvre, pas une autorisation générale.
- Que TikTok sache enregistrer ses propres vidéos ne dit rien du droit d'un tiers de les extraire.
  Une plateforme peut faire ce que ses CGU interdisent aux autres : c'est la définition même
  d'une plateforme.

Ce que la cible fait, elle, c'est extraire **sans filigrane** et hors de l'application, sur des
vidéos dont l'auteur a parfois fermé le téléchargement. Ce n'est pas le bouton de TikTok
réimplémenté ; c'est le contournement du bouton absent. *(Confiance élevée sur le
fonctionnement du bouton ; je n'ai pas revérifié les CGU TikTok ligne par ligne.)*

## 2. « Libretube et beaucoup d'autres ne sont pas sanctionnés »

Exact pour l'existence, inexact pour « non sanctionné ».

- **NewPipe / LibreTube** vivent en pointillé : YouTube casse régulièrement leur extraction, par
  changements de chiffrement, blocages 403 et, en 2024, par injection des publicités côté serveur.
  Ils ne sont pas « autorisés », ils sont **tolérés par intermittence** — c'est un rapport de
  force, pas une permission. *(Confiance élevée sur les blocages répétés ; les détails de calendrier
  mériteraient vérification.)*
- **youtube-dl** a été retiré de GitHub en octobre 2020 sur notification DMCA de la RIAA, puis
  rétabli après l'intervention de l'EFF. Un projet non sanctionné qui reçoit une DMCA et doit
  mobiliser des avocats, c'est la sanction en action. *(Confiance élevée.)*
- **Snaptube lui-même** — la cible que tu me demandes de copier — n'est pas distribué sur le Play
  Store. Il s'installe en sideload depuis son site, précisément parce que la distribution
  officielle lui a été fermée. Sa popularité n'est pas son autorisation. *(Confiance élevée.)*
- Et depuis mi-2024, les CGU de YouTube interdisent explicitement les contournements côté client.
  *(Confiance moyenne sur la formulation exacte, élevée sur le fond.)*

Conclusion factuelle : ces outils existent et fonctionnent, tu as raison. « Non sanctionné » est
faux ; « pas encore inquiété » serait plus juste, et ce n'est pas un argument de droit.

## 3. Ce que je concède vraiment

| Ton argument | Ma réponse |
| --- | --- |
| Ce n'est pas illégal en soi, c'est du droit contractuel | Vrai. Ce n'est pas du piratage, c'est une rupture de CGU. Je ne prétends pas que tu commettes un crime. |
| Ces outils existent, sont populaires, rendent service | Vrai, et je ne le conteste pas. |
| Une app privée ne change rien à qui est responsable | Faux en partie : le code circule, le dépôt devient public tôt ou tard, et un dépôt public est un dépôt public. |
| Tu ne veux pas, c'est tout | Non. Ce n'est pas un caprice, et j'explique pourquoi ci-dessous. |

## 4. Pourquoi je m'arrête là quand même

Parce que la partie refusée **n'est pas une fonctionnalité parmi d'autres : c'est le produit tout
entier**. Snaptube sans l'extraction de pages, c'est un gestionnaire de téléchargement Android
correct — ce que Selene est en train de devenir. Snaptube avec, c'est l'outil qui récupère les
vidéos des plateformes contre leur gré. Je peux écrire le premier ; je n'écris pas le second.

Ce n'est pas un jugement sur toi, ni une leçon de morale. C'est la ligne de ce que j'accepte de
produire, et elle est stable : elle ne dépend ni du nom du dépôt, ni de sa visibilité, ni du fait
que d'autres l'aient déjà franchie.

## 5. La sortie de secours existe

`docs/EXTERNAL-RESOLVER.md` : Selene sait dialoguer avec un pont que **tu** fais tourner. Tu
installes ou écris l'extracteur que tu veux, sur ta machine, et Selene télécharge, met en file,
reprend, chiffre. La fonctionnalité est là ; le code qui décode les pages n'est pas de moi.

C'est le maximum que je peux donner sans me contredire, et c'est plus que ce que j'annonçais au
tour précédent.
