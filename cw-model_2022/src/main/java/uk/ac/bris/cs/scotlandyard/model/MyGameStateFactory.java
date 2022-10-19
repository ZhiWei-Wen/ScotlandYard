package uk.ac.bris.cs.scotlandyard.model;

import ch.qos.logback.core.pattern.IdentityCompositeConverter;
import com.google.common.collect.ImmutableList;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import uk.ac.bris.cs.scotlandyard.model.Board.GameState;
import uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Factory;
import uk.ac.bris.cs.scotlandyard.model.Piece.MrX;
import uk.ac.bris.cs.scotlandyard.model.Move.*;

import java.security.PrivateKey;
import java.util.*;

import static uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Ticket.DOUBLE;
import static uk.ac.bris.cs.scotlandyard.model.ScotlandYard.Ticket.SECRET;

/**
 * cw-model
 * Stage 1: Complete this class
 */
public final class MyGameStateFactory implements Factory<GameState> {

	private final class MyGameState implements GameState {
		private boolean turn; // True is mrX's, otherwise is detectives';
		private int round;
		private final GameSetup setup;
		private ImmutableSet<Piece> remaining;
		private ImmutableList<LogEntry> log;
		private Player mrX;
		private List<Player> detectives;
		private ImmutableSet<Move> moves;
		private ImmutableSet<Piece> winner;
		private static ImmutableMap<Integer, Player> Location; // Key: the Detective's location, value: Player type  detectives
		private static ImmutableMap<Piece, Player> Match;


		private MyGameState(
				final boolean turn,
				final GameSetup setup,
				final ImmutableSet<Piece> remaining,
				final ImmutableList<LogEntry> log,
				final Player mrX,
				final List<Player> detectives){
			this.turn = turn;
			this.round = log.size();
			this.remaining = remaining;
			this.setup = setup;
			this.log = log;
			this.mrX = mrX;
			this.detectives = detectives;

			if(setup.moves.isEmpty()) throw new IllegalArgumentException("Moves is empty!");
			if(detectives.isEmpty()) throw new IllegalArgumentException("Detectives are null");
			if(setup.graph.nodes().isEmpty()) throw new IllegalArgumentException("Graph is empty");
			if(!mrX.isMrX()) throw new IllegalArgumentException("No MrX");
			Map<Integer,Player> Location = new HashMap<>();
			Map<Piece, Player> Match = new HashMap<>();
			for (Player player: detectives) {
				Match.put(player.piece(), player);
				if (player.equals(null))
					throw new IllegalArgumentException("Detective is null");
				if (player.has(DOUBLE))
					throw new IllegalArgumentException("Detectives have double ticket");
				if (player.has(SECRET))
					throw new IllegalArgumentException("Detectives have secret ticket");
				if (Collections.frequency(detectives,player) > 1)
					throw new IllegalArgumentException("Duplicate Detectives");
				if (Location.containsKey(player.location()))
					throw new IllegalArgumentException("Location Overlap Between Detectives");
						else Location.putIfAbsent(player.location(), player);
			}
			Location.putIfAbsent(mrX.location(), mrX);
			this.Match = ImmutableMap.copyOf(Match);
			this.Location = ImmutableMap.copyOf(Location);
			Set<Move> NewMoves = new HashSet<>();
			if (!remaining.contains(mrX.piece())) {
					for (Player player : detectives)
						if (remaining.contains(player.piece()))
							NewMoves.addAll(makeSingleMoves(setup, detectives, mrX, player.location()));
			}
					else {
						NewMoves.addAll(makeDoubleMoves(round, setup, mrX, mrX.location()));
				    	NewMoves.addAll(makeSingleMoves(setup, detectives, mrX, mrX.location()));
					}


			this.winner = DeterminedWinners();


			if (!this.winner.isEmpty())
				this.moves = ImmutableSet.of();
					else this.moves = ImmutableSet.copyOf(NewMoves);

		}

		private static Set<SingleMove> makeSingleMoves(GameSetup setup,
													   List<Player> detectives,
													   Player player,
													   int source){
			// TODO create an empty collection of some sort, say, HashSet, to store all the SingleMove we generate
			Set<SingleMove> singleMoves = new HashSet<>();
			Player target = Location.get(source);
			for(int destination : setup.graph.adjacentNodes(source)) {
				// TODO find out if destination is occupied by a detective
				//  if the location is occupied, don't add to the collection of moves to return
				if (Location.containsKey(destination))
					if (Location.get(destination).isDetective())
						continue;

				for(ScotlandYard.Transport t : setup.graph.edgeValueOrDefault(source, destination, ImmutableSet.of()) ) {
					// TODO find out if the player has the required tickets
					//  if it does, construct a SingleMove and add it the collection of moves to return

					if (target.tickets().get(t.requiredTicket()) > 0) {
						SingleMove NewMove = new SingleMove(target.piece(),source,t.requiredTicket(),destination);
						singleMoves.add(NewMove);
					}
				}
					if(target.isMrX() && target.hasAtLeast(SECRET, 1)) {
						SingleMove SecretMove = new SingleMove(target.piece(), source, SECRET, destination);
						singleMoves.add(SecretMove);
					}
				// TODO consider the rules of secret moves here
				//  add moves to the destination via a secret ticket if there are any left with the player

			}

			// TODO return the collection of moves
			return singleMoves;
		}

		private  static Set<DoubleMove> makeDoubleMoves (int round, GameSetup setup,
													   Player player,
													   int source) {
			Set<DoubleMove> doubleMoves = new HashSet<>();
			Player target = Location.get(source);
			if (!player.hasAtLeast(DOUBLE, 1))
				return doubleMoves;
			if (setup.moves.size() -  round < 2)
				return doubleMoves;
			for (int destination1 : setup.graph.adjacentNodes(source)) {
				if (Location.containsKey(destination1))
					if (Location.get(destination1).isDetective())
						continue;
				  for (ScotlandYard.Transport t1 : setup.graph.edgeValueOrDefault(source, destination1, ImmutableSet.of())) {
					if (target.hasAtLeast(t1.requiredTicket(), 1) || target.hasAtLeast(SECRET, 1))
						for (int destination2 : setup.graph.adjacentNodes(destination1)) {
							if (Location.containsKey(destination2))
								if (Location.get(destination2).isDetective())
									continue;
							for (ScotlandYard.Transport t2 : setup.graph.edgeValueOrDefault(destination1, destination2, ImmutableSet.of())) {
								//consider that MrX use the same type of ticket
								if (t1.requiredTicket().equals(t2.requiredTicket()) && !target.hasAtLeast(t2.requiredTicket(), 2))
									continue;
								// consider that MrX doesn't use SECRET tickets first
								if (target.hasAtLeast(t2.requiredTicket(), 1) && target.hasAtLeast(t1.requiredTicket(), 1)) {
									DoubleMove NewMove = new DoubleMove(target.piece(),
											source,
											t1.requiredTicket(),
											destination1,
											t2.requiredTicket(),
											destination2);
									doubleMoves.add(NewMove);
									if (target.hasAtLeast(SECRET, 1)) {
										DoubleMove SecretMove1 = new DoubleMove(target.piece(),
												source,
												SECRET,
												destination1,
												t2.requiredTicket(),
												destination2);
										doubleMoves.add(SecretMove1);
										DoubleMove SecretMove2 = new DoubleMove(target.piece(),
												source,
												t1.requiredTicket(),
												destination1,
												SECRET,
												destination2);
										doubleMoves.add(SecretMove2);
									}
								}
								// consider the situation that MrX uses SECRET tickets
								if (!target.hasAtLeast(t1.requiredTicket(),1) && target.hasAtLeast(t2.requiredTicket(), 1)&& target.hasAtLeast(SECRET,1)) {
									DoubleMove SecretMove1 = new DoubleMove(target.piece(),
											source,
											SECRET,
											destination1,
											t2.requiredTicket(),
											destination2);
									doubleMoves.add(SecretMove1);
								}
								if (!target.hasAtLeast(t2.requiredTicket(),1) &&  target.hasAtLeast(t1.requiredTicket(), 1) &&target.hasAtLeast(SECRET,1)) {
									DoubleMove SecretMove2 = new DoubleMove(target.piece(),
											source,
											t1.requiredTicket(),
											destination1,
											SECRET,
											destination2);
									doubleMoves.add(SecretMove2);
								}
								if (target.hasAtLeast(SECRET, 2)) {
									DoubleMove SecretMove = new DoubleMove(target.piece(),
											source,
											SECRET,
											destination1,
											SECRET,
											destination2);
									doubleMoves.add(SecretMove);
								}
							}
					}
				}
			}
			return doubleMoves;
		}

		private  ImmutableSet<Piece> DeterminedWinners () {
			Set<Move> TestMove1 = new HashSet<>();
			Set<Move> TestMove2 = new HashSet<>();
			boolean q = true;
			for (Player detective:detectives)
				TestMove1.addAll(makeSingleMoves(setup,detectives,mrX,detective.location()));
			TestMove2.addAll(makeDoubleMoves(round,setup,mrX,mrX.location()));
			TestMove2.addAll(makeSingleMoves(setup,detectives,mrX, mrX.location()));
			for (Player detective: detectives)
				if (detective.location() == mrX.location())
					return Match.keySet();

			//if all the reachable nodes are ocuppied by detectives then MrX lose
			for (int p: setup.graph.adjacentNodes(mrX.location()))
				if (!Location.containsKey(p))
					q = false;
			if (q)
				return Match.keySet();

			// At the last of round, Detectives fail to catch MrX and lose
			if (round == setup.moves.size() && turn)
				return ImmutableSet.of(mrX.piece());

			// no available move for MrX and he loses
			if (TestMove2.isEmpty() && turn)
				return Match.keySet();

			// no available move for detectives and they lose
			if (TestMove1.isEmpty())
				return ImmutableSet.of(mrX.piece());
			return ImmutableSet.of();
		}
		@Nonnull
		@Override public GameSetup getSetup() {  return setup; }

		@Nonnull
		@Override  public ImmutableSet<Piece> getPlayers() {
			Set<Piece> Players = new HashSet<>();
			Players.add(mrX.piece());
			for (Player player: detectives)
				Players.add(player.piece());
			return ImmutableSet.copyOf(Players);
			}

		@Nonnull
		@Override
		public Optional<TicketBoard> getPlayerTickets(Piece piece) {
			  class MyPlayerTickets implements TicketBoard {
				 final ImmutableMap<ScotlandYard.Ticket,Integer> tickets;
				 private MyPlayerTickets(Player player) {
					 tickets = player.tickets();
				 }
				 @Override
				 public int getCount(@Nonnull ScotlandYard.Ticket ticket) {
					 return tickets.get(ticket);
				 }
			 }
			if (piece.isMrX())
				return Optional.of(new MyPlayerTickets(mrX));
			if (piece.isDetective()) {
				for (Player player: detectives)
					if (player.piece().equals(piece))
						return Optional.of(new MyPlayerTickets(player));
			}
			return Optional.empty();
		}

		@Nonnull
		@Override
		public ImmutableSet<Move> getAvailableMoves() {
			return moves;
		}

		@Nonnull
		@Override
		public Optional<Integer> getDetectiveLocation(Piece.Detective detective) {

				for (Player player: detectives)
					if (player.piece().equals(detective))
						return Optional.of(player.location());

			return Optional.empty();
		}

		@Nonnull
		@Override
		public ImmutableList<LogEntry> getMrXTravelLog() {
			return log;
		}

		@Nonnull
		@Override public GameState advance(Move move) {

			if(!winner.isEmpty()) throw new IllegalArgumentException("Game is over");
			if(!moves.contains(move)) throw new IllegalArgumentException("Illegal move: "+move);

			SingleMove sgMove= move.accept(new Visitor<>() {
				@Override
				public  SingleMove visit(SingleMove singleMove) {
					return singleMove;
				}

				@Override
				public  SingleMove visit(DoubleMove doubleMove) {
					return null;
				}
			});
			DoubleMove dbMove = move.accept(new Visitor<>() {
				@Override
				public DoubleMove visit(SingleMove singleMove) {
					return null;
				}

				@Override
				public DoubleMove visit(DoubleMove doubleMove) {
					return doubleMove;
				}
			});
			List<LogEntry> NewLog = new ArrayList<>(log);
			//NewLog.addAll(log);
			Map<Integer, Player> NewLocations = new HashMap<>(Location);
			//NewLocations.putAll(Location);
			Set<Piece> NewRemaining = new HashSet<>(remaining);
			//NewRemaining.addAll(remaining);
			List<Player> NewDetectives = new ArrayList<>(detectives);
			//NewDetectives.addAll(detectives);
			if (sgMove != null) {
				if (sgMove.commencedBy().isMrX()) {
					if (setup.moves.get(round) )
						NewLog.add(LogEntry.reveal(sgMove.ticket,sgMove.destination));
							else NewLog.add(LogEntry.hidden(sgMove.ticket));
					NewLocations.remove(sgMove.source(),mrX);
					mrX = mrX.use(sgMove.ticket);
					mrX = mrX.at(sgMove.destination);
					NewLocations.put(sgMove.destination,mrX);
					NewRemaining.remove(mrX.piece());
					for(Player detective:detectives)
						if(!makeSingleMoves(setup,detectives,detective,detective.location()).isEmpty())
							NewRemaining.add(detective.piece());
					turn = !turn;
				}
				else {
					for (Player player : detectives)
						if (player.piece().equals(sgMove.commencedBy())) {
							NewLocations.remove(sgMove.source(),player);
							NewDetectives.remove(player);
							player = player.at(sgMove.destination)
											.use(sgMove.ticket);
							mrX = mrX.give(sgMove.ticket);
							NewDetectives.add(player);
							NewLocations.put(sgMove.destination,player);
							NewRemaining.remove(player.piece());
						}
					if (NewRemaining.isEmpty()) {
						round += 1;
						turn = !turn;
						NewRemaining.add(mrX.piece());
					}

				}
			}

			if (dbMove != null) {
				if (setup.moves.get(round))
					NewLog.add(LogEntry.reveal(dbMove.ticket1, dbMove.destination1));
						else NewLog.add(LogEntry.hidden(dbMove.ticket1));
				if (setup.moves.get(round+1))
					NewLog.add(LogEntry.reveal(dbMove.ticket2,dbMove.destination2));
						else NewLog.add(LogEntry.hidden(dbMove.ticket2));
				NewLocations.remove(dbMove.source(), mrX);
				mrX = mrX.use(dbMove.ticket1)
						 .use(dbMove.ticket2)
						 .use(DOUBLE)
						 .at(dbMove.destination2);
				NewLocations.put(dbMove.destination2,mrX);
				NewRemaining.remove(mrX.piece());
				for(Player detective:detectives)
					if(!makeSingleMoves(setup,detectives,detective,detective.location()).isEmpty())
						NewRemaining.add(detective.piece());
				turn = !turn;
			}
			Location = ImmutableMap.copyOf(NewLocations);
			detectives = NewDetectives;
			return new MyGameState(turn, setup,ImmutableSet.copyOf(NewRemaining),ImmutableList.copyOf(NewLog),mrX,detectives);
		}

		@Nonnull
		@Override
		public ImmutableSet<Piece> getWinner() {
			return winner;
		}
	}

	@Nonnull @Override public GameState build(
			GameSetup setup,
			Player mrX,
			ImmutableList<Player> detectives) {

		// TODO
		return new MyGameState(true,setup, ImmutableSet.of(MrX.MRX), ImmutableList.of(), mrX, detectives);
		//throw new RuntimeException("Implement me!");

	}

}
