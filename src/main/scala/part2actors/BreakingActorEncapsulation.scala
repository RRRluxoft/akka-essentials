package part2actors

import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.actor.typed.scaladsl.Behaviors

import java.util.UUID
import scala.collection.mutable.{Map => MutableMap}

/*
  NEVER PASS MUTABLE STATE TO OTHER ACTORS.
  NEVER PASS THE CONTEXT REFERENCE TO OTHER ACTORS.
  Same for Futures.
 */
object BreakingActorEncapsulation {

  final case class CardId(value: String) extends Product with Serializable

  // naive bank account
  trait AccountCommand
  case class Deposit(cardId: CardId, amount: BigDecimal)  extends AccountCommand
  case class Withdraw(cardId: CardId, amount: BigDecimal) extends AccountCommand
  case class CreateCreditCard(cardId: CardId)             extends AccountCommand
  case object CheckCardStatuses                           extends AccountCommand

  trait CreditCardCommand
  case class AttachToAccount(
    balances: MutableMap[CardId, BigDecimal],
    cards: MutableMap[CardId, ActorRef[CreditCardCommand]]
  )                       extends CreditCardCommand
  case object CheckStatus extends CreditCardCommand

  object NaiveBankAccount {
    def apply(): Behavior[AccountCommand] = Behaviors.setup { context =>
      val accountBalances: MutableMap[CardId, BigDecimal] = MutableMap()
      val cardMap: MutableMap[CardId, ActorRef[CreditCardCommand]] = MutableMap()

      Behaviors.receiveMessage {
        case CreateCreditCard(cardId) =>
          // create child
          val creditCardRef = context.spawn(CreditCard(cardId), cardId.value)
          // FIXME: BUG here !!!
          // give a referral bonus
          accountBalances += cardId -> 10


          context.log.info(s"Creating $cardId")
          // send an AttachToAccount msg to the child
          creditCardRef ! AttachToAccount(accountBalances, cardMap)
          // change behavior
          Behaviors.same

        case Deposit(cardId, amount) =>
          val oldBalance = accountBalances.getOrElse(cardId, BigDecimal(0))
          context.log.info(s"Depositing $amount via card ${cardId.value}")
          accountBalances += cardId -> (oldBalance + amount)
          Behaviors.same

        case Withdraw(cardId, amount) =>
          val oldBalance = accountBalances.getOrElse(cardId, BigDecimal(0))
          if (oldBalance < amount) {
            context.log.info(s"Attempt Withdrawal of $amount via card ${cardId.value} : influence funds")
            Behaviors.same
          } else {
            context.log.info(s"Withdrawing $amount via card ${cardId.value}")
            accountBalances += cardId -> (oldBalance - amount)
            Behaviors.same
          }

        case CheckCardStatuses =>
          context.log.info(s"Checking all card statuses")
          cardMap.values.foreach(cardRef => cardRef ! CheckStatus)
          Behaviors.same
      }
    }
  }

  object CreditCard {
    def apply(cardId: CardId): Behavior[CreditCardCommand] = Behaviors.receive { (context, msg) =>
      msg match {
        case AttachToAccount(balances, cards) =>
          context.log.info(s"[${cardId.value}] Attaching to bank account")
          balances += (cardId -> BigDecimal(0))
          cards += (cardId -> context.self)
          Behaviors.same

        case CheckStatus =>
            context.log.info(s"[$cardId] All things green.")
            Behaviors.same

        case _           => ???
      }
    }
  }

  def main(args: Array[String]): Unit = {
    val userGuardian: Behavior[Unit] = Behaviors.setup { context =>
      val bankAccount = context.spawn(NaiveBankAccount(), "bankAccount")
      val card_1 = CardId(UUID.randomUUID().toString)
      val card_gold = CardId("GOLD")
      val card_premium = CardId("premium")
      bankAccount ! CreateCreditCard(card_1)
      bankAccount ! CreateCreditCard(card_gold)
      bankAccount ! CreateCreditCard(card_premium)

      bankAccount ! Deposit(card_gold, BigDecimal(1000))
      bankAccount ! Deposit(card_premium, BigDecimal(510))

      bankAccount ! CheckCardStatuses

      Behaviors.empty
    }

    val system = ActorSystem(userGuardian, "DemoNaiveBankAccount")
    Thread.sleep(1000)
    system.terminate()
  }
}
