package io.atomix.protocols.log.partition.impl;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.atomix.log.Term;
import io.atomix.log.TermProvider;
import io.atomix.primitive.partition.GroupMember;
import io.atomix.primitive.partition.PrimaryElection;
import io.atomix.primitive.partition.PrimaryElectionEvent;
import io.atomix.primitive.partition.PrimaryTerm;

/**
 * Log partition term provider.
 */
public class LogPartitionTermProvider implements TermProvider {
  private final PrimaryElection election;
  private final GroupMember member;
  private final int replicationFactor;
  private final Map<Consumer<Term>, Consumer<PrimaryElectionEvent>> eventListeners = new ConcurrentHashMap<>();

  public LogPartitionTermProvider(PrimaryElection election, GroupMember member, int replicationFactor) {
    this.election = election;
    this.member = member;
    this.replicationFactor = replicationFactor;
  }

  @Override
  public CompletableFuture<Term> getTerm() {
    return election.getTerm().thenApply(this::toTerm);
  }

  private Term toTerm(PrimaryTerm primaryTerm) {
    long term = primaryTerm.getTerm();
    String leader = primaryTerm.getPrimary().getMemberId();
    List<String> followers = primaryTerm.getCandidatesList()
        .subList(1, Math.min(primaryTerm.getCandidatesList().size(), replicationFactor))
        .stream()
        .map(GroupMember::getMemberId)
        .collect(Collectors.toList());
    return new Term(term, leader, followers);
  }

  @Override
  public void addListener(Consumer<Term> listener) {
    Consumer<PrimaryElectionEvent> primaryElectionListener = event -> listener.accept(toTerm(event.getTerm()));
    eventListeners.put(listener, primaryElectionListener);
    election.addListener(primaryElectionListener);
  }

  @Override
  public void removeListener(Consumer<Term> listener) {
    Consumer<PrimaryElectionEvent> primaryElectionListener = eventListeners.remove(listener);
    if (primaryElectionListener != null) {
      election.removeListener(primaryElectionListener);
    }
  }

  @Override
  public CompletableFuture<Void> join() {
    return election.enter(member).thenApply(v -> null);
  }

  @Override
  public CompletableFuture<Void> leave() {
    return CompletableFuture.completedFuture(null);
  }
}
