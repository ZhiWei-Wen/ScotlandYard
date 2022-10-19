package uk.ac.bris.cs.scotlandyard.model;

import java.util.*;
import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableSet;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Factory;

/**
 * cw-model
 * Stage 2: Complete this class
 */
public final class MyModelFactory implements Factory<Model> {
	private final class MyModel implements Model {
		private Board.GameState GameStates;
		private ImmutableSet<Observer> Observers;

		private MyModel (Board.GameState gameStates, ImmutableSet<Observer> observers) {
			this.Observers = observers;
			this.GameStates = gameStates;
		}

		@Nonnull
		@Override
		public Board getCurrentBoard() {
			return GameStates;
		}

		@Override
		public void registerObserver(@Nonnull Observer observer) {
			if (observer == null) throw new NullPointerException("null observer");
			if (Observers.contains(observer)) throw new IllegalArgumentException("observer registered twice");
			Set<Observer> NewObserver = new HashSet<>(Observers);
			NewObserver.add(observer);
			Observers = ImmutableSet.copyOf(NewObserver);
		}

		@Override
		public void unregisterObserver(@Nonnull Observer observer) {
			if (observer == null) throw new NullPointerException("null observer");
			if(!Observers.contains(observer)) throw new IllegalArgumentException("observer is not in the Observers");
			Set<Observer> NewObserver = new HashSet<>(Observers);
			NewObserver.remove(observer);
			Observers = ImmutableSet.copyOf(NewObserver);
		}

		@Nonnull
		@Override
		public ImmutableSet<Observer> getObservers() {
			return Observers;
		}

		@Override
		public void chooseMove(@Nonnull Move move) {
			GameStates = GameStates.advance(move);
			for (Observer observer:Observers) {
				observer.onModelChanged(GameStates, Observer.Event.MOVE_MADE);
				if (!GameStates.getWinner().isEmpty())
					observer.onModelChanged(GameStates,Observer.Event.GAME_OVER);
			}
		}
	}
		@Nonnull
		@Override
		public Model build(GameSetup setup,
						   Player mrX,
						   ImmutableList<Player> detectives) {
			// TODO
			Board.GameState gameState = new MyGameStateFactory().build(setup,mrX,detectives);
			//throw new RuntimeException("Implement me!");
			return new MyModel(gameState,ImmutableSet.of());
		}

}
